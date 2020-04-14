package it.gaussproject.semanticengine.utils;

public class HumidexCalculator {

	public static double calculate(double temperature, double humidity) {
		double dewPoint = temperature-((100-humidity)/5);
		return temperature+(5/9)*(6.11*Math.exp(5417.7530*(1/273.16-1/(273.15+dewPoint))-10));
	}
}
