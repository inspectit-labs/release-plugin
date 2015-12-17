Jenkins Release Plugin
===================== 

What is it?
--------------------------

	This plugin aims to automate several tasks of the release process of InspectIT.
	It offers the following features:
		- update / create JIRA Tickets
		- update / create JIRA releases
		- publish release notes and artifacts on GitHub
		- publish release notes on Confluence
	
	
How to use it
--------------

Execute 

	clean install hpi:hpi -DskipTests=true
	
to create the .hpi file.
This file can then be installed to your Jenkins Instance using the Jenkins settings menu.

