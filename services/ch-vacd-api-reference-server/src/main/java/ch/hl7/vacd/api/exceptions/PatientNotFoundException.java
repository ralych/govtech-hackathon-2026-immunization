package ch.hl7.vacd.api.exceptions;

public class PatientNotFoundException extends Exception {

	private static final long serialVersionUID = 2104786354502083968L;

	public PatientNotFoundException(String string) {
		super(string);
	}

}
