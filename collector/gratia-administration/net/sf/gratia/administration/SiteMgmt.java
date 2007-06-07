package net.sf.gratia.administration;

import net.sf.gratia.services.*;

import java.io.*;
import java.net.*;

import java.util.StringTokenizer;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Enumeration;

import javax.servlet.*;
import javax.servlet.http.*;

import java.sql.*;

import java.util.regex.*;

public class SiteMgmt extends HttpServlet 
{
		XP xp = new XP();
		//
		// database related
		//
		String driver = "";
		String url = "";
		String user = "";
		String password = "";
		Connection connection;
		Statement statement;
		ResultSet resultSet;
		//
		// processing related
		//
		String html = "";
		String row = "";
		Pattern p = Pattern.compile("<tr>.*?</tr>",Pattern.MULTILINE + Pattern.DOTALL);
		Matcher m = null;
		StringBuffer buffer = new StringBuffer();
		//
		// globals
		//
		HttpServletRequest request;
		HttpServletResponse response;
		boolean initialized = false;
		//
		// support
		//
		String dq = "\"";
		String comma = ",";
		String cr = "\n";
		Hashtable table = new Hashtable();
		String newname = "<New CE Name>";

    public void init(ServletConfig config) throws ServletException 
		{
    }
    
		public void openConnection()
		{
				try
						{
								Properties p = Configuration.getProperties();
								driver = p.getProperty("service.mysql.driver");
								url = p.getProperty("service.mysql.url");
								user = p.getProperty("service.mysql.user");
								password = p.getProperty("service.mysql.password");
						}
				catch (Exception ignore)
						{
						}
				try
						{
								Class.forName(driver).newInstance();
								connection = DriverManager.getConnection(url,user,password);
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}
		}

		public void closeConnection()
		{
				try
						{
								connection.close();
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}
		}

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
		{
				openConnection();
				this.request = request;
				this.response = response;
				table = new Hashtable();
				setup();
				process();
				response.setContentType("text/html");
				response.setHeader("Cache-Control", "no-cache"); // HTTP 1.1
				response.setHeader("Pragma", "no-cache"); // HTTP 1.0
				request.getSession().setAttribute("table",table);
				PrintWriter writer = response.getWriter();
				writer.write(html);
				writer.flush();
				writer.close();
				closeConnection();
		}

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
		{
				openConnection();
				this.request = request;
				this.response = response;
				table = (Hashtable) request.getSession().getAttribute("table");
				update();
				closeConnection();
				response.sendRedirect("site.html");
		}

		public void setup()
		{
				html = xp.get(request.getRealPath("/") + "site.html");
				m = p.matcher(html);
				while (m.find())
						{
								String temp = m.group();
								if (temp.indexOf("#index#") > 0)
										{
												row = temp;
												break;
										}
						}
		}

		public void process()
		{
				int index = 0;
				String command = "select * from Site order by SiteName";
				buffer = new StringBuffer();

				try
						{
								statement = connection.prepareStatement(command);
								resultSet = statement.executeQuery(command);

								while(resultSet.next())
										{
												String newrow = new String(row);
												newrow = xp.replaceAll(newrow,"#index#","" + index);
												newrow = xp.replace(newrow,"#dbid#","" + resultSet.getInt(1));
												newrow = xp.replace(newrow,"#cename#",resultSet.getString(2));
												table.put("index:" + index,"" + index);
												table.put("dbid:" + index,resultSet.getString(1));
												table.put("cename:" + index,resultSet.getString(2));
												index++;
												buffer.append(newrow);
										}
								resultSet.close();
								statement.close();
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}
				for (int j = 0; j < 5; j++)
						{
								String newrow = new String(row);
								newrow = xp.replaceAll(newrow,"#index#","" + index);
								newrow = xp.replace(newrow,"#cename#",newname);
								table.put("index:" + index,"" + index);
								table.put("cename:" + index,newname);
								index++;
								buffer.append(newrow);
						}
				html = xp.replace(html,row,buffer.toString());
		}

		public void update()
		{
				int index;
				String key = "";
				String oldvalue = "";
				String newvalue = "";

				for (index = 0; index < 1000; index++)
						{
								key = "index:" + index;
								oldvalue = (String) table.get(key);
								newvalue = (String) request.getParameter(key);
								if (oldvalue == null)
										break;
								key = "cename:" + index;
								oldvalue = (String) table.get(key);
								newvalue = (String) request.getParameter(key);
								if (oldvalue.equals(newvalue))
										continue;
								if (oldvalue.equals(newname))
										insert(index);
								else
										update(index);
						}
		}

		public void update(int index)
		{
				String command = 
						"update Site set" + cr +
						" SiteName = " + dq + (String) request.getParameter("cename:" + index) + dq + cr +
						" where siteid = " + request.getParameter("dbid:" + index);
				try
						{
								statement = connection.createStatement();
								statement.executeUpdate(command);
								// connection.commit();
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}
				finally
						{
								try
										{
												statement.close();
										}
								catch (Exception ignore)
										{
										}
						}
		}

		public void insert(int index)
		{
				String command = 
						"insert into Site (SiteName) values(" + 
						dq + (String) request.getParameter("cename:" + index) + dq + ")";
				try
						{
								statement = connection.createStatement();
								statement.executeUpdate(command);
								// connection.commit();
						}
				catch (Exception e)
						{
								System.out.println("command: " + command);
								e.printStackTrace();
						}
				finally
						{
								try
										{
												statement.close();
										}
								catch (Exception ignore)
										{
										}
						}
		}
}
