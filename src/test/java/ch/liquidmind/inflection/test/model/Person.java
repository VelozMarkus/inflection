package ch.liquidmind.inflection.test.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Person extends IdentifiableObject
{
	private String firstName , lastName , title;
	private String telephone , mobile , email;
	private Gender gender;
	private Date dateOfBirth;
	private List< Address > addresses = new ArrayList< Address >();
	
	public String getFirstName()
	{
		return firstName;
	}

	public void setFirstName( String firstName )
	{
		this.firstName = firstName;
	}

	public String getLastName()
	{
		return lastName;
	}

	public void setLastName( String lastName )
	{
		this.lastName = lastName;
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle( String title )
	{
		this.title = title;
	}

	public String getTelephone()
	{
		return telephone;
	}

	public void setTelephone( String telephone )
	{
		this.telephone = telephone;
	}

	public String getMobile()
	{
		return mobile;
	}

	public void setMobile( String mobile )
	{
		this.mobile = mobile;
	}

	public String getEmail()
	{
		return email;
	}

	public void setEmail( String email )
	{
		this.email = email;
	}

	public Gender getGender()
	{
		return gender;
	}

	public void setGender( Gender gender )
	{
		this.gender = gender;
	}

	public Date getDateOfBirth()
	{
		return dateOfBirth;
	}

	public void setDateOfBirth( Date dateOfBirth )
	{
		this.dateOfBirth = dateOfBirth;
	}

	public List< Address > getAddresses()
	{
		return addresses;
	}
}