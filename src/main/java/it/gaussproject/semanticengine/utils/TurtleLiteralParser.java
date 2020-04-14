package it.gaussproject.semanticengine.utils;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ResourceFactory;

public class TurtleLiteralParser {

	//TODO: this is a fast hack, better find a reliable way to convert turtle typed literals
	public static Literal parse(String literal) {
		Literal simpleResultLiteral;
		if(literal.contains("^^")) {
			int index = literal.indexOf("^^");
			String value = literal.substring(0, index);
			String type = literal.substring(index+2, literal.length());
			if(type.contains(":")) {
				type = type.substring(type.indexOf(":")+1);
			}
			if(type.contains("#")) {
				type = type.substring(type.indexOf("#")+1);
			}
			simpleResultLiteral = ResourceFactory.createTypedLiteral(value, new XSDDatatype(type));
		} else {
			simpleResultLiteral = ResourceFactory.createStringLiteral(literal);
		}
		return simpleResultLiteral;
	}

}
