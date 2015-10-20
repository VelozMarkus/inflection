package ch.liquidmind.inflection.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import ch.liquidmind.inflection.compiler.CompilationUnit.CompilationUnitCompiled.PackageImport;
import ch.liquidmind.inflection.compiler.CompilationUnit.CompilationUnitCompiled.PackageImport.PackageImportType;
import ch.liquidmind.inflection.compiler.CompilationUnit.CompilationUnitCompiled.TypeImport;
import ch.liquidmind.inflection.grammar.InflectionParser.APackageContext;
import ch.liquidmind.inflection.grammar.InflectionParser.AliasContext;
import ch.liquidmind.inflection.grammar.InflectionParser.AliasableClassSelectorContext;
import ch.liquidmind.inflection.grammar.InflectionParser.AnnotationContext;
import ch.liquidmind.inflection.grammar.InflectionParser.ClassSelectorContext;
import ch.liquidmind.inflection.grammar.InflectionParser.CompilationUnitContext;
import ch.liquidmind.inflection.grammar.InflectionParser.DefaultAccessMethodModifierContext;
import ch.liquidmind.inflection.grammar.InflectionParser.ExcludableClassSelectorContext;
import ch.liquidmind.inflection.grammar.InflectionParser.ExcludeViewModifierContext;
import ch.liquidmind.inflection.grammar.InflectionParser.ExtendedTaxonomyContext;
import ch.liquidmind.inflection.grammar.InflectionParser.IncludableClassSelectorContext;
import ch.liquidmind.inflection.grammar.InflectionParser.IncludeViewModifierContext;
import ch.liquidmind.inflection.grammar.InflectionParser.PackageImportContext;
import ch.liquidmind.inflection.grammar.InflectionParser.SimpleTypeContext;
import ch.liquidmind.inflection.grammar.InflectionParser.TaxonomyDeclarationContext;
import ch.liquidmind.inflection.grammar.InflectionParser.TaxonomyExtensionsContext;
import ch.liquidmind.inflection.grammar.InflectionParser.TaxonomyNameContext;
import ch.liquidmind.inflection.grammar.InflectionParser.TypeContext;
import ch.liquidmind.inflection.grammar.InflectionParser.TypeImportContext;
import ch.liquidmind.inflection.grammar.InflectionParser.UsedClassSelectorContext;
import ch.liquidmind.inflection.grammar.InflectionParser.ViewDeclarationContext;
import ch.liquidmind.inflection.grammar.InflectionParser.WildcardSimpleTypeContext;
import ch.liquidmind.inflection.loader.SystemTaxonomyLoader;
import ch.liquidmind.inflection.model.AccessType;
import ch.liquidmind.inflection.model.SelectionType;
import ch.liquidmind.inflection.model.compiled.AnnotationCompiled;
import ch.liquidmind.inflection.model.compiled.TaxonomyCompiled;
import ch.liquidmind.inflection.model.compiled.ViewCompiled;

public class Pass2Listener extends AbstractInflectionListener
{
	private TaxonomyCompiled currentTaxonomyCompiled;
	private Set< ViewCompiled > currentViewsCompiled;
	private List< AnnotationCompiled > currentAnnotationsCompiled;
	private SelectionType currentSelectionType;
	
	public Pass2Listener( CompilationUnit compilationUnit )
	{
		super( compilationUnit );
	}

	@Override
	public void exitCompilationUnit( CompilationUnitContext compilationUnitContext )
	{
		for ( TypeImport typeImport : getTypeImports().values() )
			if ( !typeImport.getWasReferenced() )
				reportWarning( typeImport.getParserRuleContext().start, typeImport.getParserRuleContext().stop, "Unused import." );
		
		for ( PackageImport packageImport : getPackageImports() )
			if ( !packageImport.getWasReferenced() && packageImport.getType().equals( PackageImportType.OTHER_PACKAGE ) && !packageImport.getName().equals( DEFAULT_PACKAGE_NAME ) )
				reportWarning( packageImport.getParserRuleContext().start, packageImport.getParserRuleContext().stop, "Unused import." );
	}

	// IMPORTS
	
	@Override
	public void enterPackageImport( PackageImportContext packageImportContext )
	{
		APackageContext aPackageContext = (APackageContext)packageImportContext.getChild( 0 );
		String packageName = getPackageName( aPackageContext );
		validateNoDuplicatePackageImport( aPackageContext, packageName );
		validateNoOverlapWithTypeImport( aPackageContext, packageName );
		getPackageImports().add( new PackageImport( packageName, aPackageContext, PackageImportType.OTHER_PACKAGE ) );
	}
	
	private void validateNoDuplicatePackageImport( APackageContext aPackageContext, String packageName )
	{
		if ( getPackageImports().contains( new PackageImport( packageName ) ) )
			reportWarning( aPackageContext.start, aPackageContext.stop, "Duplicate import." );
	}	
	
	private void validateNoOverlapWithTypeImport( APackageContext aPackageContext, String packageName )
	{
		for ( TypeImport typeImport : getTypeImports().values() )
		{
			String typePackageName = getPackageName( typeImport.getName() );
			
			if ( packageName.equals( typePackageName ) )
				reportWarning( aPackageContext.start, aPackageContext.stop, "Overlapping import: symbol already implicitly imported by 'import " + typeImport.getName() + ";" );
		}
	}
	
	@Override
	public void enterTypeImport( TypeImportContext typeImportContext )
	{
		TypeContext typeContext = (TypeContext)typeImportContext.getChild( 0 );
		String typeName = getTypeName( typeContext );
		String simpleTypeName = getSimpleTypeName( typeContext );
		validateNoDuplicateTypeImport( typeContext, simpleTypeName );
		validateNoOverlapWithPackageImport( typeContext, simpleTypeName );
		getTypeImports().put( simpleTypeName, new TypeImport( typeName, typeContext ) );
	}
	
	private void validateNoDuplicateTypeImport( TypeContext typeContext, String simpleTypeName )
	{
		if ( getTypeImports().keySet().contains( simpleTypeName ) )
			reportWarning( typeContext.start, typeContext.stop, "Duplicate import." );
	}
	
	private void validateNoOverlapWithPackageImport( TypeContext typeContext, String simpleTypeName )
	{
		String packageNameOfType = getPackageName( typeContext );
		
		if ( getPackageImports().contains( new PackageImport( packageNameOfType ) ) )
			reportWarning( typeContext.start, typeContext.stop, "Overlapping import: symbol already implicitly imported by 'import " + packageNameOfType + ".*;" );
	}
	
	// TAXONOMIES
	
	@Override
	public void enterTaxonomyDeclaration( TaxonomyDeclarationContext taxonomyDeclarationContext )
	{
		currentAnnotationsCompiled = new ArrayList< AnnotationCompiled >();
	}
	
	@Override
	public void enterTaxonomyName( TaxonomyNameContext taxonomyNameContext )
	{
		currentTaxonomyCompiled = getTaxonomyCompiled( getTaxonomyName( taxonomyNameContext ) );
		currentTaxonomyCompiled.getAnnotationsCompiled().addAll( currentAnnotationsCompiled );
	}

	// This method searches the taxonomies of the compilation unit rather than all known
	// taxonomies within the compilation job.
	private TaxonomyCompiled getTaxonomyCompiled( String taxonomyName )
	{
		TaxonomyCompiled foundTaxonomyCompiled = null;
		
		for ( TaxonomyCompiled taxonomyCompiled : getCompilationUnit().getCompilationUnitCompiled().getTaxonomiesCompiled() )
		{
			if ( taxonomyCompiled.getName().equals( taxonomyName ) )
			{
				foundTaxonomyCompiled = taxonomyCompiled;
				break;
			}
		}
		
		return foundTaxonomyCompiled;
	}

	@Override
	public void enterTaxonomyExtensions( TaxonomyExtensionsContext taxonomyExtensionsContext )
	{
		// Taxonomies that don't extend anything else extend ch.liquidmind.inflection.Taxonomy
		// by default (analogous to java.lang.Object)
		if ( taxonomyExtensionsContext.getChildCount() == 0 )
			currentTaxonomyCompiled.getExtendedTaxonomies().add( SystemTaxonomyLoader.TAXONOMY );
	}

	@Override
	public void enterExtendedTaxonomy( ExtendedTaxonomyContext extendedTaxonomyContext )
	{
		TypeContext typeContext = (TypeContext)extendedTaxonomyContext.getChild( 0 );
		String resolvedTaxonomyReference = resolveTaxonomyReference( typeContext );
		
		if ( resolvedTaxonomyReference != null )
			currentTaxonomyCompiled.getExtendedTaxonomies().add( resolvedTaxonomyReference );
	}
	
	private String resolveTaxonomyReference( TypeContext typeContext )
	{
		// TODO Implement by looking first in the type imports and second in the 
		// package imports. Look for matching taxonomies as well as matching classes
		// in the latter and report an error if the resolution is ambiguous.
		
		String resolvedTaxonomyReference = null;
		List< String > matches = new ArrayList< String >();
		String packageName = getPackageName( typeContext );
		String simpleName = getSimpleTypeName( typeContext );
		
		if ( packageName.isEmpty() )
		{
			// First, try matching to a type import.
			TypeImport typeImport = getTypeImports().get( simpleName );
			
			if ( typeImport != null )
			{
				typeImport.setWasReferenced( true );
				String match = typeImport.getName();
				
				if ( taxonomyExists( match ) )
					matches.add( match );
			}

			// Then, try matching to one of the package imports.
			for ( PackageImport packageImport : getPackageImports() )
			{
				String potentialMatch = ( packageImport.getName().isEmpty() ? simpleName : packageImport.getName() + "." + simpleName );
				
				if ( taxonomyExists( potentialMatch ) )
				{
					packageImport.setWasReferenced( true );
					matches.add( potentialMatch );
				}
			}
		}
		else
		{
			String typeName = getTypeName( typeContext );
			
			if ( taxonomyExists( typeName ) )
				matches.add( typeName );
		}
		
		if ( matches.size() == 0 )
			reportError( typeContext.start, typeContext.stop, "Could not find referenced taxonomy (Did you misspell? Or forget an import? Or a jar?)." );
		else if ( matches.size() == 1 )
			resolvedTaxonomyReference = matches.get( 0 );
		else if ( matches.size() > 1 )
			reportError( typeContext.start, typeContext.stop, "Taxonomy reference is ambiguous; could refer to any of: " + String.join( ", ", matches ) + "." );
		else
			throw new IllegalStateException( "Unexpected value for matches.size()." );
		
		return resolvedTaxonomyReference;
	}
	
	private boolean taxonomyExists( String name )
	{
		boolean taxonomyExists = false;
		
		if ( getKnownTaxonomiesCompiled().get( name ) != null ||
				getTaxonomyLoader().loadTaxonomy( name ) != null )
			taxonomyExists = true;
	
		return taxonomyExists;
	}

	@Override
	public void enterDefaultAccessMethodModifier( DefaultAccessMethodModifierContext defaultAccessMethodModifierContext )
	{
		AccessType accessType;
		
		if ( defaultAccessMethodModifierContext.getChildCount() == 0 )
			accessType = AccessType.INHERITED;
		else if ( defaultAccessMethodModifierContext.getChildCount() == 3 )
			accessType = AccessType.valueOf( defaultAccessMethodModifierContext.getChild( 1 ).getText().toUpperCase() );
		else
			throw new IllegalStateException( "Unexpected value for defaultAccessMethodModifierContext.getChildCount()." );
		
		currentTaxonomyCompiled.setDefaultAccessType( accessType );
	}
	
	// VIEWS

	@Override
	public void enterViewDeclaration( ViewDeclarationContext viewDeclarationContext )
	{
		currentAnnotationsCompiled = new ArrayList< AnnotationCompiled >();
		currentViewsCompiled = new HashSet< ViewCompiled >();
	}
	
	@Override
	public void enterIncludableClassSelector( IncludableClassSelectorContext includableClassSelectorContext )
	{
		Set< String > matchingClasses = getMatchingClasses( includableClassSelectorContext );
		setupViewsCompiled( matchingClasses );
	}

	@Override
	public void enterExcludableClassSelector( ExcludableClassSelectorContext excludableClassSelectorContext )
	{
		Set< String > matchingClasses = getMatchingClasses( excludableClassSelectorContext );
		setupViewsCompiled( matchingClasses );
	}
	
	private Set< String > getMatchingClasses( ParserRuleContext classSelectorContext )
	{
		SimpleTypeContext simpleTypeContext = getRuleContextRecursive( classSelectorContext, SimpleTypeContext.class );
		WildcardSimpleTypeContext wildcardSimpleTypeContext = getRuleContextRecursive( classSelectorContext, WildcardSimpleTypeContext.class );
		ParserRuleContext simpleClassSelectorContext = ( simpleTypeContext == null ? wildcardSimpleTypeContext : simpleTypeContext );
		APackageContext packageContext = getRuleContextRecursive( classSelectorContext, APackageContext.class );
		String packagePrefix = ( packageContext == null ? DEFAULT_PACKAGE_NAME : packageContext.getText() + "." );
		String packagePrefixRegEx = ( packagePrefix.equals( DEFAULT_PACKAGE_NAME ) ? "[a-zA-Z0-9_$.]*?" : packagePrefix.replace( ".", "\\." ) );
		String classSelector = simpleClassSelectorContext.getText();
		String classSelectorRegEx = packagePrefixRegEx + classSelector.replace( ".", "\\." ).replace( "*", "[a-zA-Z0-9_$]*?" );
		
		return getMatchingClasses( packageContext, classSelectorRegEx );
	}
	
	// Note that the list of potentially matching classes could be cached
	// to optimize performance.
	private Set< String > getMatchingClasses( APackageContext packageContext, String classSelectorRegEx )
	{
		Set< String > matchingClasses = new HashSet< String >();

		if ( packageContext == null )
		{
			matchingClasses.addAll( getMatchingClassesFromPackageImports( getPackageImports(), classSelectorRegEx ) );
		}
		else
		{
			Set< PackageImport > packageImports = new HashSet< PackageImport >();
			packageImports.add( new PackageImport( packageContext.getText() ) );
			matchingClasses.addAll( getMatchingClassesFromPackageImports( packageImports, classSelectorRegEx ) );
		}

		matchingClasses.addAll( getMatchingClassesFromTypeImports( getTypeImports().values(), classSelectorRegEx ) );
		
		return matchingClasses;
	}
	
	private Set< String > getMatchingClassesFromTypeImports( Collection< TypeImport > typeImports, String classSelectorRegEx )
	{
		Set< String > matchingClasses = new HashSet< String >();

		for ( TypeImport typeImport : typeImports )
		{
			String potentialMatch = typeImport.getName();
			
			if ( potentialMatch.matches( classSelectorRegEx ) && getClass( potentialMatch ) != null )
			{
				matchingClasses.add( potentialMatch );
				typeImport.setWasReferenced( true );
			}
		}
		
		return matchingClasses;
	}
	
	private Set< String > getMatchingClassesFromPackageImports( Set< PackageImport > packageImports, String classSelectorRegEx )
	{
		Set< String > matchingClasses = new HashSet< String >();

		for ( PackageImport packageImport : packageImports )
		{
			String packageName = packageImport.getName();
			String packgeRegEx;
			
			if ( packageName.equals( DEFAULT_PACKAGE_NAME ) )
				packgeRegEx = "[a-zA-Z0-9_$]*";
			else
				packgeRegEx = packageName.replace( ".", "\\." ) + "\\.[a-zA-Z0-9_$]*";

			Set< String > typesInPackage = getMatchingClasses( packgeRegEx );
			
			for ( String potentialMatch : typesInPackage )
			{
				if ( potentialMatch.matches( classSelectorRegEx ) )
				{
					matchingClasses.add( potentialMatch );
					packageImport.setWasReferenced( true );
				}
			}
		}
		
		return matchingClasses;
	}
	
	private Set< String > getMatchingClasses( String packageRegEx )
	{
		Set< String > matchingClasses = new HashSet< String >();
		
		Reflections reflections = new Reflections( "", getTaxonomyLoader().getClassLoader(), new SubTypesScanner( false ) );
		Set< String > typesInPackage = reflections.getAllTypes();
		
		for ( String typeInPackage : typesInPackage )
			if ( typeInPackage.matches( packageRegEx ) )
				matchingClasses.add( typeInPackage );
		
		return matchingClasses;
	}
	
	private void setupViewsCompiled( Set< String > matchingClasses )
	{
		for ( String matchingClass : matchingClasses )
		{
			ViewCompiled viewCompiled = null;
			
			for ( ViewCompiled existingViewCompiled : currentTaxonomyCompiled.getViewsCompiled() )
			{
				if ( existingViewCompiled.getName().equals( matchingClass ) )
				{
					viewCompiled = existingViewCompiled;
					break;
				}
			}
			
			if ( viewCompiled == null )
			{
				viewCompiled = new ViewCompiled( matchingClass );
				currentTaxonomyCompiled.getViewsCompiled().add( viewCompiled );
			}

			viewCompiled.getAnnotationsCompiled().addAll( currentAnnotationsCompiled );
			viewCompiled.setSelectionType( currentSelectionType );
			
			currentViewsCompiled.add( viewCompiled );
		}
	}

	@Override
	public void enterAliasableClassSelector( AliasableClassSelectorContext aliasableClassSelectorContext )
	{
		// Note that I'm assuming that there is exactly one matching class and therefore
		// invoking iterator().next() is safe; the checks should have already been performed
		// in enterViewDeclaration().
		ClassSelectorContext classSelectorContext = (ClassSelectorContext)aliasableClassSelectorContext.getChild( 0 );
		AliasContext aliasContext = (AliasContext)aliasableClassSelectorContext.getChild( 2 );
		String alias = aliasContext.getText();
		String className = getMatchingClasses( classSelectorContext ).iterator().next();
		String fqAlias = ( getPackageName().equals( DEFAULT_PACKAGE_NAME ) ? alias : getPackageName() + "." + alias );
		validateAliasNameNotInConflict( aliasContext, fqAlias );
		
		for ( ViewCompiled viewCompiled : currentTaxonomyCompiled.getViewsCompiled() )
		{
			if ( viewCompiled.getName().equals( className ) )
			{
				viewCompiled.setAlias( alias );
				break;
			}
		}
	}
	
	// TODO: Currently, checks on conflicts with other alias are only performed within
	// a given taxonomy. Need to think about how to deal with taxonomy inheritence
	// (can aliases override other aliases? can they override views?). Also, should 
	// views be referencable via their alias?
	private void validateAliasNameNotInConflict( AliasContext aliasContext, String alias )
	{
		String fqAlias = ( getPackageName().equals( DEFAULT_PACKAGE_NAME ) ? alias : getPackageName() + "." + alias );
		String classSelectorRegEx = fqAlias.replace( ".", "\\." );
		Set< String > matchingTypes = new HashSet< String >();
		Set< PackageImport > packageImports = new HashSet< PackageImport >();
		packageImports.add( new PackageImport( getPackageName() ) );
		matchingTypes.addAll( getMatchingClassesFromPackageImports( packageImports, classSelectorRegEx ) );
		matchingTypes.addAll( getMatchingClassesFromTypeImports( getTypeImports().values(), classSelectorRegEx ) );
		
		if ( taxonomyExists( fqAlias ) )
			matchingTypes.add( fqAlias );
		
		if ( getKnownAliases().contains( alias ) )
			matchingTypes.add( fqAlias );
		
		if ( !matchingTypes.isEmpty() )
			reportError( aliasContext.start, aliasContext.stop, "Alias name is in conflict with existing types: " + String.join( ", ", matchingTypes ) + "." );
	}
	
	private Set< String > getKnownAliases()
	{
		Set< String > knownAliases = new HashSet< String >();
		
		for ( ViewCompiled viewCompiled : currentTaxonomyCompiled.getViewsCompiled() )
			if ( viewCompiled.getAlias() != null )
				knownAliases.add( viewCompiled.getAlias() );
		
		return knownAliases;
	}
	
	@Override
	public void enterUsedClassSelector( UsedClassSelectorContext usedClassSelectorContext )
	{
		Set< String > matchingClasses = getMatchingClasses( usedClassSelectorContext );
		validateUsedClasses( usedClassSelectorContext, matchingClasses );
		String usedClassName = matchingClasses.iterator().next();

		for ( ViewCompiled currentViewCompiled : currentViewsCompiled )
			currentViewCompiled.getUsedClasses().add( usedClassName );
	}
	
	private void validateUsedClasses( UsedClassSelectorContext usedClassSelectorContext, Set< String > matchingClasses )
	{
		if ( matchingClasses.size() == 0 )
		{
			reportError( usedClassSelectorContext.start, usedClassSelectorContext.stop, "Could not find referenced class (Did you misspell? Or forget an import?)." );
		}
		else if ( matchingClasses.size() == 1 )
		{
			if ( getClass( matchingClasses.iterator().next() ) == null )
				reportError( usedClassSelectorContext.start, usedClassSelectorContext.stop, "Could not find referenced class (Did you misspell? Or forget an import? Or a jar?)." );
		}
		else if ( matchingClasses.size() > 1 )
		{
			reportError( usedClassSelectorContext.start, usedClassSelectorContext.stop, "Class reference is ambiguous; could refer to any of: " + String.join( ", ", matchingClasses ) );
		}
	}
	
	private Set< String > getMatchingClasses( UsedClassSelectorContext usedClassSelectorContext )
	{
		SimpleTypeContext simpleTypeContext = getRuleContextRecursive( usedClassSelectorContext, SimpleTypeContext.class );
		APackageContext packageContext = getRuleContextRecursive( usedClassSelectorContext, APackageContext.class );
		String packagePrefix = ( packageContext == null ? DEFAULT_PACKAGE_NAME : packageContext.getText() + "." );
		String packagePrefixRegEx = ( packagePrefix.equals( DEFAULT_PACKAGE_NAME ) ? "[a-zA-Z0-9_$.]*?" : packagePrefix.replace( ".", "\\." ) );
		String classSelector = simpleTypeContext.getText();
		String classSelectorRegEx = packagePrefixRegEx + classSelector.replace( ".", "\\." ).replace( "*", "[a-zA-Z0-9_$]*?" );
		
		return getMatchingClasses( packageContext, classSelectorRegEx );
	}

	// MEMBERS
	
	// MISC

	@Override
	public void enterAnnotation( AnnotationContext annotationContext )
	{
		currentAnnotationsCompiled.add( new AnnotationCompiled( annotationContext.getText() ) );
	}
	
	@Override
	public void enterIncludeViewModifier( IncludeViewModifierContext includeViewModifierContext )
	{
		currentSelectionType = SelectionType.INCLUDE;
	}

	@Override
	public void enterExcludeViewModifier( ExcludeViewModifierContext excludeViewModifierContext )
	{
		currentSelectionType = SelectionType.EXCLUDE;
	}
}