package net.sf.gratia.storage;

public class Cluster
{
  private long clusterid;
  private String name;

  public Cluster()
  {
  }

  public String toString() {
    String output = "Cluster info: " + "clusterid: " + clusterid + " Name: " + name;
    return output;
  }

  public long getClusterid() {
    return clusterid;
  }

  public void setClusterid(long clusterid) {
    this.clusterid = clusterid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
