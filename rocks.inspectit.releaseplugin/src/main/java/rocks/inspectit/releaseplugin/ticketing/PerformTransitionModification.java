package rocks.inspectit.releaseplugin.ticketing;

import hudson.Extension;
import hudson.model.Descriptor;

import java.io.PrintStream;

import org.apache.commons.lang.text.StrSubstitutor;
import org.kohsuke.stapler.DataBoundConstructor;

import rocks.inspectit.releaseplugin.JIRAAccessTool;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.google.common.base.Optional;


/**
 * 
 * Modificaiton that performs a transition on a ticket (changes its status).
 * 
 * @author Jonas Kunz
 *
 */
public class PerformTransitionModification extends TicketModification {

	/**
	 * The name of the transition.
	 */
	private String transitionName;
	
	/**
	 * The comemnt to leave after the transition has been performed.
	 */
	private String comment;
	
	
	

	/**
	 * Databound constructor, called by Jenkins.
	 * @param transitionName
	 * 		the name of the transition
	 * @param comment
	 * 		the comment to leave.
	 */
	@DataBoundConstructor
	public PerformTransitionModification(String transitionName, String comment) {
		super();
		this.transitionName = transitionName;
		this.comment = comment;
	}

	


	public String getTransitionName() {
		return transitionName;
	}


	public String getComment() {
		return comment;
	}




	/**
	 * Descriptor class.	
	 * @author JKU
	 *
	 */
	@Extension public static class DescribtorImpl extends Descriptor<TicketModification> {

		@Override
		public String getDisplayName() {
			return "Perform Transition";
		}
		
	}


	@Override
	public void apply(String ticketKey, JIRAAccessTool jira, StrSubstitutor varReplacer, PrintStream logger) {
		
		//theses actions can not be done by an update put request, so we use the jira access tool
		
		String transitionName = varReplacer.replace(this.transitionName);
		String comment = varReplacer.replace(Optional.fromNullable(this.comment).or(""));
		
		Issue issue = jira.getTicketByKey(ticketKey);
		int id = -1;
		for (Transition trans : jira.getAvailableTransitions(issue)) {
			if (trans.getName().equalsIgnoreCase(transitionName)) {
				id = trans.getId();
			}
		}
		
		if (id == -1) {
			throw new RuntimeException("The transition with the name \"" + transitionName + "\" is either non existent or not accessible for the Ticket " + ticketKey);
		}
		
		TransitionInput input;
		if (comment.isEmpty()) {
			input = new TransitionInput(id);
		} else {
			input = new TransitionInput(id, Comment.valueOf(comment));			
		}
		
		jira.performTransition(issue, input);
		
		
	}
}
