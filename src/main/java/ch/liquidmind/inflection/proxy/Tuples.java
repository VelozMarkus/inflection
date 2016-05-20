package ch.liquidmind.inflection.proxy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import __java.lang.__Class;
import __java.lang.__ClassLoader;
import __java.lang.reflect.__Field;
import ch.liquidmind.inflection.Auxiliary;
import ch.liquidmind.inflection.Inflection;
import ch.liquidmind.inflection.model.external.Taxonomy;
import ch.liquidmind.inflection.model.external.View;

public class Tuples
{
	public static class ObjectsTuple
	{
		private Proxy proxy;
		private Object object;
		private Object auxiliary;
		
		public ObjectsTuple( Proxy proxy, Object object, Object auxiliary )
		{
			super();
			this.proxy = proxy;
			this.object = object;
			this.auxiliary = auxiliary;
		}
	
		@SuppressWarnings( "unchecked" )
		public < T > T getObject( ObjectType objectType )
		{
			T targetObject = null;
			
			if ( objectType.equals( ObjectType.Proxy ) )
				targetObject = (T)proxy;
			else if ( objectType.equals( ObjectType.Object ) )
				targetObject = (T)object;
			else if ( objectType.equals( ObjectType.Auxiliary ) )
				targetObject = (T)auxiliary;
			else
				throw new IllegalStateException( "Target object cannot be identified." );
			
			return targetObject;
		}
		
		public Proxy getProxy()
		{
			return proxy;
		}
	
		public Object getObject()
		{
			return object;
		}
	
		public Object getAuxiliary()
		{
			return auxiliary;
		}
	
		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + ( ( auxiliary == null ) ? 0 : System.identityHashCode( auxiliary ) );
			result = prime * result + ( ( object == null ) ? 0 : System.identityHashCode( object ) );
			result = prime * result + ( ( proxy == null ) ? 0 : System.identityHashCode( proxy ) );
			return result;
		}
	
		@Override
		public boolean equals( Object obj )
		{
			if ( this == obj )
				return true;
			if ( obj == null )
				return false;
			if ( getClass() != obj.getClass() )
				return false;
			ObjectsTuple other = (ObjectsTuple)obj;
			if ( auxiliary == null )
			{
				if ( other.auxiliary != null )
					return false;
			}
			else if ( !auxiliary.equals( other.auxiliary ) )
				return false;
			if ( object == null )
			{
				if ( other.object != null )
					return false;
			}
			else if ( !object.equals( other.object ) )
				return false;
			if ( proxy == null )
			{
				if ( other.proxy != null )
					return false;
			}
			else if ( !proxy.equals( other.proxy ) )
				return false;
			return true;
		}
	}
	
	public static class ClassesTuple
	{
		private Class< ? > proxyClass;
		private Class< ? > objectClass;
		private Class< ? > auxiliaryClass;
		
		public ClassesTuple( Class< ? > proxyClass, Class< ? > objectClass, Class< ? > auxiliaryClass )
		{
			super();
			this.proxyClass = proxyClass;
			this.objectClass = objectClass;
			this.auxiliaryClass = auxiliaryClass;
		}
	
		public Class< ? > getProxyClass()
		{
			return proxyClass;
		}
	
		public Class< ? > getObjectClass()
		{
			return objectClass;
		}
	
		public Class< ? > getAuxiliaryClass()
		{
			return auxiliaryClass;
		}
	}

	private static final Map< Class< ? >, Class< ? > > PROXY_BASE_CLASSES = new HashMap< Class< ? >, Class< ? > >();
	public static final Map< Class< ? >, Class< ? > > COLLECTION_CLASSES = new HashMap< Class< ? >, Class< ? > >();
	
	static
	{
		PROXY_BASE_CLASSES.put( List.class, ListProxy.class );
		PROXY_BASE_CLASSES.put( Set.class, SetProxy.class );
		PROXY_BASE_CLASSES.put( Map.class, MapProxy.class );
		PROXY_BASE_CLASSES.put( Iterator.class, IteratorProxy.class );
		
		COLLECTION_CLASSES.put( ListProxy.class, ArrayList.class );
		COLLECTION_CLASSES.put( SetProxy.class, HashSet.class );
		COLLECTION_CLASSES.put( MapProxy.class, HashMap.class );
	}
	
	public static Class< ? > getProxyBaseClass( Class< ? > viewedClass )
	{
		Class< ? > proxyBaseClass = null;
		
		if ( viewedClass.isArray() )
			proxyBaseClass = ListProxy.class;
		else
			proxyBaseClass = PROXY_BASE_CLASSES.get( viewedClass );
		
		return proxyBaseClass;
	}

	private Taxonomy taxonomy;
	private Map< Integer, ObjectsTuple > objectsTuples = new HashMap< Integer, ObjectsTuple >();
	private Map< Class< ? >, ClassesTuple > classesTuples = new HashMap< Class< ? >, ClassesTuple >();
	
	public Tuples( Taxonomy taxonomy )
	{
		this.taxonomy = taxonomy;
	}
	
	public enum ObjectType
	{
		Proxy, Object, Auxiliary
	}
	
	public < T > T getObject( ObjectType objectType, Object key )
	{
		T targetObject;
		
		try
		{
			ObjectsTuple tuple = getObjectTuple( key );
			targetObject = tuple.getObject( objectType );
		}
		catch ( NoProxyException e )
		{
			targetObject = null;
		}
		
		return targetObject;
	}

	private ObjectsTuple getObjectTuple( Object key )
	{
		ObjectsTuple objectsTuple = objectsTuples.get( System.identityHashCode( key ) );
		
		if ( objectsTuple == null )
		{
			objectsTuple = createObjectTuple( key );
			objectsTuples.put( System.identityHashCode( objectsTuple.getObject() ), objectsTuple );
			objectsTuples.put( System.identityHashCode( objectsTuple.getProxy() ), objectsTuple );
			
			if ( objectsTuple.getAuxiliary() != null )
				objectsTuples.put( System.identityHashCode( objectsTuple.getAuxiliary() ), objectsTuple );
		}
		
		return objectsTuple;
	}
	
	@SuppressWarnings( "unchecked" )
	private ObjectsTuple createObjectTuple( Object object )
	{
		Class< ? > theClass = object.getClass();
		ClassesTuple classesTuple = getClassesTuple( object );
		View view = Inflection.getView( (Class< Proxy >)classesTuple.getProxyClass() );
		
		Proxy proxy = determineObject( theClass, classesTuple.getProxyClass(), object );
		Object viewableObject = determineObject( theClass, classesTuple.getObjectClass(), object );
		Object auxiliary = determineAuxilliaryObject( theClass, classesTuple.getAuxiliaryClass(), object, taxonomy, view );
		
		ObjectsTuple objectsTuple = new ObjectsTuple( proxy, viewableObject, auxiliary );
		
		return objectsTuple;
	}
	
	@SuppressWarnings( "unchecked" )
	private < T > T determineAuxilliaryObject( Class< ? > classA, Class< ? > classB, Object objectA, Taxonomy taxonomy, View view )
	{
		return (T)( classA.equals( classB ) ? objectA : ( classB == null ? null : createAuxiliaryObject( classB, taxonomy, view ) ) );
	}
	
	@SuppressWarnings( "unchecked" )
	private < T > T createAuxiliaryObject( Class< ? > theClass, Taxonomy taxonomy, View view )
	{
		T auxiliaryObject = (T)__Class.newInstance( theClass );
		__Field.set( AUXILIARY_TAXONOMY, auxiliaryObject, taxonomy );
		__Field.set( AUXILIARY_VIEW, auxiliaryObject, view );
		
		return auxiliaryObject;
	}
	
	@SuppressWarnings( "unchecked" )
	private < T > T determineObject( Class< ? > classA, Class< ? > classB, Object objectA )
	{
		return (T)( classA.equals( classB ) ? objectA : ( classB == null ? null : __Class.newInstance( classB ) ) );
	}

	public static final Field AUXILIARY_TAXONOMY = __Class.getDeclaredField( Auxiliary.class, "taxonomy" );
	public static final Field AUXILIARY_VIEW = __Class.getDeclaredField( Auxiliary.class, "view" );
	
	static
	{
		AUXILIARY_TAXONOMY.setAccessible( true );
		AUXILIARY_VIEW.setAccessible( true );
	}

	private ClassesTuple getClassesTuple( Object key )
	{
		Class< ? > lookupClass;
		
		if ( key instanceof Auxiliary )
		{
			View view = (View)__Field.get( AUXILIARY_VIEW, key );
			lookupClass = view.getViewedClass();
		}
		else
		{
			lookupClass = key.getClass();
		}
		
		return getClassesTuple( lookupClass );
	}
	
	private ClassesTuple getClassesTuple( Class< ? > key )
	{
		ClassesTuple classesTuple = classesTuples.get( key );
		
		if ( classesTuple == null )
		{
			classesTuple = createClassesTuple( key );
			classesTuples.put( classesTuple.getProxyClass(), classesTuple );
			classesTuples.put( classesTuple.getObjectClass(), classesTuple );
		}
		
		return classesTuple;
	}
	
	private ClassesTuple createClassesTuple( Class< ? > aClass )
	{
		ClassesTuple classTuple = null;
		
		if ( isCollection( aClass ) )
			classTuple = createClassesTupleFromCollection( aClass );
		else
			classTuple = createClassesTupleFromNonCollection( aClass );

		return classTuple;
	}
	
	private boolean isCollection( Class< ? > aClass )
	{
		Set< Class< ? > > intersection = new HashSet< Class< ? > >( PROXY_BASE_CLASSES.keySet() );
		intersection.retainAll( getInterfacesRecursive( aClass ) );
		
		return !intersection.isEmpty();
	}
	
	private ClassesTuple createClassesTupleFromCollection( Class< ? > aClass )
	{
		ClassesTuple classesTuple;
		
		if ( Proxy.class.isAssignableFrom( aClass ) )
			classesTuple = createClassesTupleFromProxyCollection( aClass );
		else
			classesTuple = createClassesTupleFromNonProxyCollection( aClass );
		
		return classesTuple;
	}
	
	private ClassesTuple createClassesTupleFromProxyCollection( Class< ? > proxyClass )
	{
		Class< ? > objectClass = null;
		Set< Class< ? > > intersection = new HashSet< Class< ? > >( COLLECTION_CLASSES.keySet() );
		intersection.retainAll( getClassesRecursive( proxyClass ) );
		Class< ? > proxyInterface = intersection.iterator().next();

		if ( intersection.size() != 1 )
			throw new IllegalStateException( "intersection should contain exactly one element." );
		
		objectClass = COLLECTION_CLASSES.get( proxyInterface );
		ClassesTuple classesTuple = new ClassesTuple( proxyClass, objectClass, null );
		
		return classesTuple;
	}
	
	private ClassesTuple createClassesTupleFromNonProxyCollection( Class< ? > objectClass )
	{
		Set< Class< ? > > intersection = new HashSet< Class< ? > >( PROXY_BASE_CLASSES.keySet() );
		intersection.retainAll( getInterfacesRecursive( objectClass ) );
		
		if ( intersection.size() != 1 )
			throw new IllegalStateException( "intersection should contain exactly one element." );
		
		Class< ? > objectInterface = intersection.iterator().next();
		String proxyClassName = ProxyGenerator.getFullyQualifiedCollectionName( taxonomy, PROXY_BASE_CLASSES.get( objectInterface ) );
		Class< ? > proxyClass = __ClassLoader.loadClass( Thread.currentThread().getContextClassLoader(), proxyClassName );
		ClassesTuple classesTuple = new ClassesTuple( proxyClass, objectClass, null );
		
		return classesTuple;
	}
	
	@SuppressWarnings( "unchecked" )
	private ClassesTuple createClassesTupleFromNonCollection( Class< ? > aClass )
	{
		View correspondingView;
		
		if ( Proxy.class.isAssignableFrom( aClass ) )
			correspondingView = Inflection.getView( (Class< Proxy >)aClass );
		else
			correspondingView = getViewByClass( aClass );
	
		Class< ? > objectClass = correspondingView.getViewedClass();
		Class< ? > auxiliaryClass = getLeafAuxiliaryClass( correspondingView );
		String proxyClassName = ProxyGenerator.getFullyQualifiedViewName( taxonomy, correspondingView );
		Class< ? > proxyClass = __ClassLoader.loadClass( taxonomy.getTaxonomyLoader().getClassLoader(), proxyClassName );
		
		ClassesTuple classesTuple = new ClassesTuple( proxyClass, objectClass, auxiliaryClass );
		
		return classesTuple;
	}
	
	private Class< ? > getLeafAuxiliaryClass( View view )
	{
		Class< ? > leafAuxiliaryClass = view.getUsedClass();
		
		
		if ( leafAuxiliaryClass == null )
		{
			View superView = taxonomy.getSuperview( view );
			
			if ( superView != null )
				leafAuxiliaryClass = getLeafAuxiliaryClass( superView );
		}
		
		return leafAuxiliaryClass;
	}
	
	private View getViewByClass( Class< ? > aClass )
	{
		try
		{
			return taxonomy.getViews().stream().filter( x -> x.getViewedClass().equals( aClass ) || ( x.getUsedClass() != null && x.getUsedClass().equals( aClass ) ) ).findFirst().get();		
		}
		catch ( NoSuchElementException e )
		{
			throw new NoProxyException();
		}
	}

	private List< Class< ? > > getClassesRecursive( Class< ? > aClass )
	{
		List< Class< ? > > classes = new ArrayList< Class< ? > >();
		classes.add( aClass );
		
		if ( aClass.getSuperclass() != null )
			classes.addAll( getClassesRecursive( aClass.getSuperclass() ) );
		
		return classes;
	}
	
	private List< Class< ? > > getInterfacesRecursive( Class< ? > aClass )
	{
		List< Class< ? > > interfaces = new ArrayList< Class< ? > >();
		interfaces.addAll( Arrays.asList( aClass.getInterfaces() ) );
		
		if ( aClass.getSuperclass() != null )
			interfaces.addAll( getInterfacesRecursive( aClass.getSuperclass() ) );
		
		return interfaces;
	}
	
}