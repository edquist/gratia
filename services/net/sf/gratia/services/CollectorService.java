package net.sf.gratia.services;

import java.util.Properties;

import java.rmi.*;
import java.io.*;
import java.util.TimeZone;
import java.util.Enumeration;

import org.objectweb.joram.client.jms.admin.*;
import org.objectweb.joram.client.jms.*;
import org.objectweb.joram.client.jms.tcp.*;

import fr.dyade.aaa.agent.AgentServer;

import javax.servlet.*;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.sql.*;

import java.security.*;

public class CollectorService implements ServletContextListener
{
		public String rmibind;
		public String rmilookup;
		public String service;

		public Properties p;

		public org.hibernate.cfg.Configuration hibernateConfiguration;
		public org.hibernate.SessionFactory hibernateFactory;

		//
		// various threads
		//

		ListenerThread thread1;
		ListenerThread thread2;
		ListenerThread thread3;
		StatusListenerThread statusListenerThread;
		ReplicationService replicationService;

		ProbeMonitorService probeMonitorService;
		RMIService rmiservice;

		public void contextInitialized(ServletContextEvent sce)
		{
				XP xp = new XP();
				String catalinaHome = "";
				String configurationPath = "";

				Enumeration iter = System.getProperties().propertyNames();
				System.out.println("");
				while(iter.hasMoreElements())
						{
								String key = (String) iter.nextElement();
								String value = (String) System.getProperty(key);
								System.out.println("Key: " + key + " value: " + value);
						}
				System.out.println("");

				try
						{
								//
								// setup configuration path/https system parameters
								//

								configurationPath = net.sf.gratia.services.Configuration.getConfigurationPath();
								System.setProperty("java.protocol.handler.pkgs","com.sun.net.ssl.internal.www.protocol");
								Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

								System.setProperty("javax.net.ssl.trustStore",configurationPath + "/truststore");
								System.setProperty("javax.net.ssl.trustStorePassword","server");

								System.setProperty("javax.net.ssl.keyStore",configurationPath + "/keystore");
								System.setProperty("javax.net.ssl.keyStorePassword","server");

								com.sun.net.ssl.HostnameVerifier hv=new com.sun.net.ssl.HostnameVerifier() 
										{
												public boolean verify(String urlHostname, String certHostname) 
												{
														System.out.println("url host name: " + urlHostname);
														System.out.println("cert host name: " + certHostname);
														System.out.println("WARNING: Hostname is not matched for cert.");
														return true;
												}
										};

								com.sun.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(hv);

								//
								// get configuration properties
								//

								p = net.sf.gratia.services.Configuration.getProperties();
								rmilookup = p.getProperty("service.rmi.rmilookup");
								rmibind = p.getProperty("service.rmi.rmibind");
								service = p.getProperty("service.rmi.service");
								
								//
								// set default timezone
								//

								TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

								//
								// initialize logging
								//

								Logging.initialize(p.getProperty("service.service.logfile"),
																	 p.getProperty("service.service.maxlog"),
																	 p.getProperty("service.service.console"),
																	 p.getProperty("service.service.level"));
								//
								// start jms
								//

								File lock = new File(net.sf.gratia.services.Configuration.getConfigurationPath() + "/s1/lock");
								if (lock.exists())
										{
												Logging.info("CollectorService: Deleting Lock File");
												lock.delete();
										}

								System.setProperty(AgentServer.CFG_DIR_PROPERTY,net.sf.gratia.services.Configuration.getConfigurationPath());
								AgentServer.init((short) 0,net.sf.gratia.services.Configuration.getConfigurationPath() + "/s1",null);
								AgentServer.start();
								Thread.sleep(10);
								Logging.info("CollectorService: JMS Server Started");

								//
								// start rmi
								//

								rmiservice = new RMIService();
								rmiservice.setDaemon(true);

								rmiservice.start();
								Thread.sleep(10);

								//
								// setup topics/q's
								//

								AdminModule.collocatedConnect("root","root");

								Queue queue1 = (Queue) Queue.create("xml-accounting-queue");
								Queue queue2 = (Queue) Queue.create("status-queue");

								User user = User.create("anonymous", "anonymous");

								queue1.setFreeReading();
								queue1.setFreeWriting();

								queue2.setFreeReading();
								queue2.setFreeWriting();

								int jmsport = Integer.parseInt(p.getProperty("service.jms.port"));

								javax.jms.ConnectionFactory cf = TcpConnectionFactory.create("localhost", jmsport);
								javax.jms.QueueConnectionFactory qcf = QueueTcpConnectionFactory.create("localhost", jmsport);
								
								//
								// start database
								//

								try
										{
												hibernateConfiguration = new org.hibernate.cfg.Configuration();
												hibernateConfiguration.addFile(new File(net.sf.gratia.services.Configuration.getJobUsagePath()));
												hibernateConfiguration.configure(new File(net.sf.gratia.services.Configuration.getHibernatePath()));

												Properties hp = new Properties();
												hp.setProperty("hibernate.connection.driver_class",p.getProperty("service.mysql.driver"));
												hp.setProperty("hibernate.connection.url",p.getProperty("service.mysql.url"));
												hp.setProperty("hibernate.connection.username",p.getProperty("service.mysql.user"));
												hp.setProperty("hibernate.connection.password",p.getProperty("service.mysql.password"));
												hibernateConfiguration.addProperties(hp);

												hibernateFactory = hibernateConfiguration.buildSessionFactory();
												Logging.info("Database Opened");
										}
								catch (Exception databaseError)
										{
												databaseError.printStackTrace();
										}

								//
								// zap database
								//

								zapDatabase();

								//
								// poke in rmi
								//

								JMSProxyImpl proxy = new JMSProxyImpl(qcf,queue1,queue2,hibernateFactory);
								Naming.rebind(rmibind + service,proxy);
								
								//
								// start msg listener
								//

								javax.jms.Connection connection = (javax.jms.Connection) cf.createConnection();
								connection.start();
								AdminModule.disconnect();

								thread1 = new ListenerThread("Listener 1",hibernateFactory,qcf,queue1);
								thread1.setPriority(Thread.MAX_PRIORITY);
								thread1.setDaemon(true);
								thread1.start();

								thread2 = new ListenerThread("Listener 2",hibernateFactory,qcf,queue1);
								thread2.setPriority(Thread.MAX_PRIORITY);
								thread2.setDaemon(true);
								thread2.start();

								thread3 = new ListenerThread("Listener 3",hibernateFactory,qcf,queue1);
								thread3.setPriority(Thread.MAX_PRIORITY);
								thread3.setDaemon(true);
								thread3.start();
								
								statusListenerThread = new StatusListenerThread(qcf,queue2);
								statusListenerThread.setDaemon(true);
								statusListenerThread.start();

								//
								// start probe monitor
								//

								probeMonitorService = new ProbeMonitorService(hibernateFactory);
								probeMonitorService.setDaemon(true);
								probeMonitorService.start();
						}
				catch (Exception e)
						{
								e.printStackTrace();
						}

				//
				// add a server cert if one isn't there
				//

				{
						String dq = "\"";
						String keystore = System.getProperty("catalina.home") + "/gratia/keystore";
						keystore = xp.replaceAll(keystore,"\\","/");
						String command1[] =
								{"keytool",
								 "-genkey",
								 "-dname",
								 "cn=server, ou=Fermi-GridAccounting, o=Fermi, c=US",
								 "-alias",
								 "server",
								 "-keystore",
								 keystore,
								 "-keypass",
								 "server",
								 "-storepass",
								 "server"};

						int exitValue1 = Execute.execute(command1);

						String command2[] =
								{"keytool",
								 "-selfcert",
								 "-alias",
								 "server",
								 "-keypass",
								 "server",
								 "-keystore",
								 keystore,
								 "-storepass",
								 "server"};

						if (exitValue1 == 0)
								Execute.execute(command2);
						FlipSSL.flip();

						//
						// start replication service
						//

						replicationService = new ReplicationService(hibernateFactory);
						replicationService.start();
				}


				//
				// wait 1 minute to create new report config for birt (giving tomcat time to deploy the war)
				//

				(new ReportSetup()).start();
		}

		public void zapDatabase()
		{
				String dq = "\"";
				XP xp = new XP();

				Properties p = net.sf.gratia.services.Configuration.getProperties();

				String driver = p.getProperty("service.mysql.driver");
				String url = p.getProperty("service.mysql.url");
				String user = p.getProperty("service.mysql.user");
				String password = p.getProperty("service.mysql.password");
	
				java.sql.Connection connection;
				Statement statement;
				ResultSet resultSet;

				try
						{
								Class.forName(driver);
								connection = (java.sql.Connection) DriverManager.getConnection(url,user,password);
						}
				catch (Exception e)
						{
								Logging.warning("CollectorService: Error During zapDatabase: " + e);
								Logging.warning(xp.parseException(e));
								return;
						}

				String commands[] = 
						{
								"alter table CETable add unique index index02(facility_name)",
								"alter table CEProbes add unique index index02(probename)",
								"insert into CETable(facility_name) values(" + dq + "Unknown" + dq + ")",
								"alter table JobUsageRecord add index index02(EndTime)",
								"alter table JobUsageRecord add index index03(ProbeName)",
								"alter table Security add unique index index02(alias)"
						};

				for (int i = 0; i < commands.length; i++)
						try
								{
										System.out.println("Executing: " + commands[i]);
										statement = connection.createStatement();
										statement.executeUpdate(commands[i]);
										System.out.println("Command: OK: " + commands[i]);
								}
						catch (Exception ignore)
								{
										System.out.println("Error Executing: " + commands[i]);
										ignore.printStackTrace();
								}

		}

		public void contextDestroyed(ServletContextEvent sce)
		{
				Logging.info("");
				Logging.info("Context Destroy Event");
				Logging.info("");
				System.exit(0);
		}


		public class ReportSetup extends Thread
		{
				public ReportSetup()
				{
				}

				public void run()
				{
						try
								{
										Thread.sleep(60 * 1000);
								}
						catch (Exception ignore)
								{
								}
						//
						// create a dummy ReportingConfig.xml for birt
						//
				
						String dq = "\"";
						XP xp = new XP();
						StringBuffer xml = new StringBuffer();
						String catalinaHome = System.getProperty("catalina.home");
						catalinaHome = xp.replaceAll(catalinaHome,"\\","/");

						xml.append("<ReportingConfig>" + "\n");
						xml.append("<DataSourceConfig" + "\n");
						xml.append("url=" + dq + p.getProperty("service.mysql.url") + dq + "\n");
						xml.append("user=" + dq + p.getProperty("service.mysql.user") + dq + "\n");
						xml.append("password=" + dq + p.getProperty("service.mysql.password") + dq + "\n");
						xml.append("/>" + "\n");
						xml.append("<PathConfig" + "\n");
						xml.append("reportsFolder=" + dq + catalinaHome + "/webapps/GratiaReports/" + dq + "\n");
						xml.append("engineHome=" + dq + catalinaHome + "/webapps/Birt/" + dq + "\n");
						xml.append("webappHome=" + dq + catalinaHome + "/webapps/GratiaReporting/" + dq + "\n");
						xml.append("/>" + "\n");
						xml.append("</ReportingConfig>" + "\n");
						xp.save(catalinaHome + "/webapps/GratiaReportConfiguration/ReportingConfig.xml",
										xml.toString());
						System.out.println("ReportConfig updated");
				}
		}


		//
		// testing
		//

		static public void main(String args[])
		{
				CollectorService service = new CollectorService();
				service.contextInitialized(null);
		}
}
