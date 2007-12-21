<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"
    import="net.sf.gratia.reporting.*"
%>    
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional //EN">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<link href="stylesheet.css" type="text/css" rel="stylesheet">
<base target="paramFrame">
<title>Gratia Accounting</title>

<script type="text/javascript">

function clearReportFrame() {
	parent.reportFrame.location = "about:blank"; 
}

function clearParamFrame() {
	parent.paramFrame.location = "about:blank"; 
}

</script>
</head>
<body>
<jsp:include page="common.jsp" />
<%
	// Get the reporting configuration setting
	UserConfiguration userConfiguration = (UserConfiguration)session.getAttribute("userConfiguration"); 
	ReportingConfiguration reportingConfiguration = (ReportingConfiguration)session.getAttribute("reportingConfiguration");
	String reportsFolder = reportingConfiguration.getReportsFolder();
	String reportingVersion = reportingConfiguration.getReportingVersion();

%>
<a href="http://opensciencegrid.org/"><img src="./images/osg-logo.gif" alt="OSG Logo" width="174" height="81" border="0"></a>
	<%
	String displayLink = null;
	String linkURL = null;
	String linkNAME = null;
	String groupName = null;
	String displayRep = "false";
	String targetFrame = "paramFrame";
	String clearFrame = "clearParamFrame(); clearReportFrame();";
	String styleClass = "menuItem";

	for(int i=0; i < userConfiguration.getMenuGroups().size(); i++)
	{
		MenuGroup menuGroup = (MenuGroup)userConfiguration.getMenuGroups().get(i);
		groupName = menuGroup.getGroup();
	%>	<hr><label class="menuGroup"><%= groupName %></label> <br /><%

		for(int z=0; z < menuGroup.getMenuItems().size(); z++)
		{
			MenuItem menuItem = (MenuItem)menuGroup.getMenuItems().get(z);

// Add the "name" of the report (title=) on the created link for display purposes
// Make sure that if this is the first argument to include "?"
// Eliminate the starting "-" and any blank spaces in the string

			String linkTitle = menuItem.getName();

			linkNAME = "- ";
			styleClass = "menuItem";
			displayRep = menuItem.getDisplay();
			if (displayRep.indexOf("true") > -1)
			{
				linkNAME = "* ";
				styleClass = "menuItem";
			}
			linkNAME = linkNAME + linkTitle;
			clearFrame = "clearReportFrame();";

			if (menuItem.getLink().indexOf("?") > -1) 
			{
			   linkURL = menuItem.getLink() + "&ReportTitle=" + linkTitle.trim() + "&displayReport=" + displayRep.trim()+ "&amp;__title";
			}else
			{
			   linkURL = menuItem.getLink() + "?ReportTitle=" + linkTitle.trim() + "&displayReport=" + displayRep.trim()+ "&amp;__title";
			}

			linkURL = linkURL.replace(" ", "%20");
			linkURL = linkURL.replace("&amp;", "&");
			linkURL = linkURL.replace("&", "&amp;");
			linkURL = linkURL.replace(">", "%3e");
			linkURL = linkURL.replace("<", "%3c");
			linkURL = linkURL.replace("\\", "%5c");

			if (displayRep.indexOf("true") > -1)
			{
				displayLink = linkURL;
			}
			%> 
			<a class="<%= styleClass %>" href="<%= linkURL %>" target="<%= targetFrame %>" onclick="<%= clearFrame %>"><%= linkNAME %></a> <br />
			<%
		}
	}
	%>
	<hr><label class="menuGroup">Static Reports</label> <br />
	 <a class = "menuItem" href="staticReports.jsp" onclick="clearReportFrame();">Static Reports</a><br />

	 <hr><label class = "menuGroup">Commands</label> <br />
	 <a class = "menuItem" href="logout.jsp" onclick="clearReportFrame();">Logout</a><br />
	 <hr><a target=_blank class="contact" href="https://twiki.grid.iu.edu/twiki/bin/view/Accounting/ContactUs">Contact us</a><br />
	 <p class = "menuVersion">Gratia Reporting Version: <%= reportingVersion %></p>
<%
if (displayLink != null)
{
   String initUrl = request.getRequestURL().toString();
   displayLink = initUrl.substring(0, initUrl.lastIndexOf("/")) + "/" + displayLink;

%>
	 <script type="text/javascript">
	 	parent.paramFrame.location = "<%= displayLink %>";
	</script>
<%
}
%>

</body>
</html>
