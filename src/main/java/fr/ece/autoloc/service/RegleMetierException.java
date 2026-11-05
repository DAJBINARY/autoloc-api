package fr.ece.autoloc.service;

/**
 * A requested action breaks a business rule; the message is shown to the user.
 */
public class RegleMetierException extends RuntimeException {

    public RegleMetierException(String message) {
        super(message);
    }
}
