package ch.liquidmind.inflection.model.external;

import java.util.List;

public interface View extends AliasableElement
{
	public Class< ? > getViewedClass();
	public List< Class< ? > > getUsedClasses();
	public Taxonomy getParentTaxonomy();
	public List< Member > getMembers();
}