package rocks.inspectit.releaseplugin.ticketing;

import hudson.model.AbstractDescribableImpl;

import java.io.PrintStream;

import org.apache.commons.lang.text.StrSubstitutor;

import rocks.inspectit.releaseplugin.JIRAAccessTool;

/**
 * 
 * Base class for all modifications selectable for a ModifyTicketsTemplate instance.
 * Extends this class to provide your own modifications for tickets. 
 * 
 * @author Jonas Kunz
 *
 */
public abstract class TicketModification extends AbstractDescribableImpl<TicketModification> {
	
	/**
	 * 
	 * Called when a ticket should be updated.
	 * 
	 * @param ticketKey
	 * 		the key of the ticket to update
	 * @param jira
	 * 		the jira access tool which may be used to perform the update
	 * @param varReplacer
	 * 		the variable replacer for parameters
	 * @param logger
	 * 		the logger of the build process
	 */
	public abstract void apply(String ticketKey, JIRAAccessTool jira, StrSubstitutor varReplacer, PrintStream logger);
	
}
