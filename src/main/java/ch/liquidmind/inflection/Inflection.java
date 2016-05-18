package ch.liquidmind.inflection;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import __java.lang.__Class;
import __java.lang.reflect.__Field;
import ch.liquidmind.inflection.exception.ExceptionWrapper;
import ch.liquidmind.inflection.loader.TaxonomyLoader;
import ch.liquidmind.inflection.model.external.Taxonomy;
import ch.liquidmind.inflection.model.external.View;
import ch.liquidmind.inflection.proxy.Proxy;
import ch.liquidmind.inflection.proxy.ProxyRegistry;
import ch.liquidmind.inflection.proxy.Tuples.ObjectType;
import ch.liquidmind.inflection.util.InflectionPrinter;

public class Inflection
{
	private static Map< Class< ? >, Taxonomy > taxonomyCache = new HashMap< Class< ? >, Taxonomy >();
	private static Map< Class< ? >, View > viewCache = new HashMap< Class< ? >, View >();
	
	public static Taxonomy getTaxonomy( Class< ? extends Proxy > proxyClass )
	{
		Taxonomy taxonomy = taxonomyCache.get( proxyClass );
		
		if ( taxonomy == null )
		{
			Field field = __Class.getDeclaredField( proxyClass, "TAXONOMY" );
			field.setAccessible( true );
			taxonomy = (Taxonomy)__Field.get( field, null );
			taxonomyCache.put( proxyClass, taxonomy );
		}
		
		return taxonomy;
	}
	
	public static View getView( Class< ? extends Proxy > proxyClass )
	{
		View view = viewCache.get( proxyClass );
		
		if ( view == null )
		{
			Field field = __Class.getDeclaredField( proxyClass, "VIEW" );
			field.setAccessible( true );
			view = (View)__Field.get( field, null );
			viewCache.put( proxyClass, view );
		}
		
		return view;
	}
	
	public static Taxonomy getTaxonomy( Proxy proxy )
	{
		return getTaxonomy( proxy.getClass() );
	}

	public static View getView( Proxy proxy )
	{
		return getView( proxy.getClass() );
	}
	
	public static < T > T cast( Taxonomy taxonomy, Class< T > theClass, Object object )
	{
		ObjectType targetObjectType = determineObjectType( theClass );
		T targetObject = ProxyRegistry.getContextProxyRegistry().getObject( taxonomy, targetObjectType, object );
		
		return targetObject;
	}
	
	public static < T > T cast( Class< T > theClass, Object object )
	{
		Taxonomy targetTaxonomy = determineTaxonomy( theClass );
		Taxonomy sourceTaxonomy = determineTaxonomy( object.getClass() );
		Taxonomy taxonomy = ( targetTaxonomy == null ? sourceTaxonomy : targetTaxonomy );
		
		if ( taxonomy == null )
			throw new IllegalStateException( "taxonomy should never be null." );
		
		return cast( taxonomy, theClass, object );
	}
	
	@SuppressWarnings( "unchecked" )
	private static Taxonomy determineTaxonomy( Class< ? > aClass )
	{
		Taxonomy taxonomy;
		
		if ( Proxy.class.isAssignableFrom( aClass ) )
		{
			taxonomy = getTaxonomy( (Class< Proxy >)aClass );
		}
		else
		{
			Auxiliary auxiliary = aClass.getAnnotation( Auxiliary.class );
			
			if ( auxiliary != null )
				taxonomy = TaxonomyLoader.getContextTaxonomyLoader().loadTaxonomy( auxiliary.value() );
			else
				taxonomy = null;
		}
		
		return taxonomy;
	}

	private static ObjectType determineObjectType( Class< ? > aClass )
	{
		ObjectType objectType;
		
		if ( Proxy.class.isAssignableFrom( aClass ) )
		{
			objectType = ObjectType.Proxy;
		}
		else
		{
			Auxiliary auxiliary = aClass.getAnnotation( Auxiliary.class );
			
			if ( auxiliary != null )
				objectType = ObjectType.Auxiliary;
			else
				objectType = ObjectType.Object;
		}
		
		return objectType;
	}

	
//	public static < T extends Proxy > T cast( Class< ? extends Proxy > proxyClass, Object viewableObject )
//	{
//		return cast( getTaxonomy( proxyClass ), viewableObject );
//	}
//
//	public static < T extends Proxy > T cast( String taxonomyName, Object viewableObject )
//	{
//		return cast( TaxonomyLoader.getContextTaxonomyLoader().loadTaxonomy( taxonomyName ), viewableObject );
//	}
//
//	public static < T extends Proxy > T cast( Taxonomy taxonomy, Object viewableObject )
//	{
//		return ProxyRegistry.getContextProxyRegistry().getProxy( taxonomy, viewableObject );
//	}
//
//	public static < T extends Proxy > T cast( Class< ? extends Proxy > proxyClass, Proxy proxy )
//	{
//		return cast( getTaxonomy( proxyClass ), proxy );
//	}
//
//	public static < T extends Proxy > T cast( String taxonomyName, Proxy proxy )
//	{
//		return cast( TaxonomyLoader.getContextTaxonomyLoader().loadTaxonomy( taxonomyName ), proxy );
//	}
//
//	public static < T extends Proxy > T cast( Taxonomy taxonomy, Proxy proxy )
//	{
//		return cast( taxonomy, cast( proxy ) );
//	}
//	
//	public static < T extends Object > T cast( Proxy proxy )
//	{
//		return ProxyRegistry.getContextProxyRegistry().getObject( proxy );
//	}
	
	public static String viewToString( Proxy proxy )
	{
		StringWriter stringWriter = new StringWriter();
		InflectionPrinter.printView( getTaxonomy( proxy ), getView( proxy ), stringWriter, true, false );
		String s = stringWriter.toString();
		
		return s;
	}
	
	public static String valueToString( Proxy proxy )
	{
		ObjectMapper mapper = new ObjectMapper();
		ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
		String s = ExceptionWrapper.ObjectWriter_writeValueAsString( writer, proxy );
		
		return s;
	}
}
