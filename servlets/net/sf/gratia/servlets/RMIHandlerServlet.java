package net.sf.gratia.servlets;

import net.sf.gratia.services.*;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.rmi.*;

public class RMIHandlerServlet extends HttpServlet 
{
    public Properties p;
		public JMSProxy proxy = null;
		XP xp = new XP();

    public void init(ServletConfig config) throws ServletException 
		{
        super.init(config);
				p = Configuration.getProperties();

				//
				// initialize logging
				//

				Logging.initialize(p.getProperty("service.rmiservlet.logfile"),
													 p.getProperty("service.rmiservlet.maxlog"),
													 p.getProperty("service.rmiservlet.console"),
													 p.getProperty("service.rmiservlet.level"));
    }
    
    public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException 
		{
				String command = null;
				String from = null;
				String to = null;
				String rmi = null;
				String arg1 = null;
				String arg2 = null;
				String arg3 = null;
				String arg4 = null;

				int argcount = 0;

				try
						{
								proxy = (JMSProxy) Naming.lookup(p.getProperty("service.rmi.rmilookup") +
																								 p.getProperty("service.rmi.service"));
						}
				catch (Exception e)
						{
								Logging.warning(xp.parseException(e));
						}

        try 
						{
								command = req.getParameter("command");
								from = req.getParameter("from");
								to = req.getParameter("to");
								rmi = req.getParameter("rmi");
								arg1 = req.getParameter("arg1");
								arg2 = req.getParameter("arg2");
								arg3 = req.getParameter("arg3");
								arg4 = req.getParameter("arg4");

								if (command != null)
										command = command.toLowerCase();

								if (arg1 != null)
										argcount++;
								if (arg2 != null)
										argcount++;
								if (arg3 != null)
										argcount++;
								if (arg4 != null)
										argcount++;

								Logging.log("RMIHandlerServlet: From: " + from);
								Logging.log("RMIHandlerServlet: To: " + to);
								Logging.log("RMIHandlerServlet: RMI: " + rmi);
								Logging.log("RMIHandlerServlet: Command: " + command);
								Logging.log("RMIHandlerServlet: Argcount: " + argcount);
								Logging.log("RMIHandlerServlet: Arg1: " + arg1);
								Logging.log("RMIHandlerServlet: Arg2: " + arg2);
								Logging.log("RMIHandlerServlet: Arg3: " + arg3);
								Logging.log("RMIHandlerServlet: Arg4: " + arg4);

								//
								// the - connect to rmi
								//

								PrintWriter writer = res.getWriter();

								if ((command.equals("update")) && (argcount == 1))
										{
												boolean status = proxy.update(arg1);
												if (status)
														writer.write("OK");
												else
														writer.write("Error");
										}
								else if ((command.equals("remoteupdate")) && (argcount == 4))
										{
												boolean status = proxy.remoteUpdate(from,Integer.parseInt(arg1),arg2,arg3,arg4);
												if (status)
														writer.write("OK");
												else
														writer.write("Error");
										}
								else if ((command.equals("statusupdate")) && (argcount == 2))
										{
												if (arg1.equals("status"))
														{
																boolean status = proxy.statusUpdate(arg2);
																if (status)
																		writer.write("OK");
																else
																		writer.write("Error");
														}
										}
								else
										{
												Logging.info("RMIHandlerServlet: Error: Unknown Command: " + command + " Or Invalid Arg Count: " + argcount);
												writer.write("Error: Unknown Command: " + command + " Or Invalid Arg Count: " + argcount);
										}
								writer.flush();
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}
		}
}
