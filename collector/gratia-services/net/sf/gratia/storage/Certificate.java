package net.sf.gratia.storage;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import net.sf.gratia.util.Base64;

public class Certificate implements Comparable
   {
      // Persistent members
      private int fCertid;
      private String fPem;
      private int fState;
      
      final int kInvalid = 0;
      final int kValid = 1;
      final int kInvalidCertificate = 2;

      // Transient members
      private X509Certificate fCert;

      public static String GeneratePem(X509Certificate cert) throws java.security.cert.CertificateEncodingException {
         
         return new String("-----BEGIN CERTIFICATE-----\n" + Base64.encodeBytes(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n");
         
      }
      
		public Certificate()
		{
         fState = kInvalidCertificate;
		}
      
		public Certificate(Certificate cert)
		{
         fCertid = cert.fCertid;
         fState = cert.fState;
         fPem = cert.fPem;
         fCert = cert.fCert;
		}
      
		public Certificate(X509Certificate cert) throws java.security.cert.CertificateException
		{
         fPem = GeneratePem( cert );
         
         fCert = cert;
         try {
            fCert.checkValidity();
            fState = kValid;
         } catch (Exception e) {
            fState = kInvalidCertificate;
         }
		}

      public int compareTo(Object obj) {
         try {
            Certificate cmp = (Certificate) obj;
            
            int result = getCert().getIssuerDN().toString().compareTo( cmp.getCert().getIssuerDN().toString() );
            if (result == 0) {
               result =  getCert().getSubjectDN().toString().compareTo( cmp.getCert().getSubjectDN().toString() );
            }
            return result;
         }
         catch (Exception e) {
            return -1;
         }
      }
      
		public int getCertid() 
		{
         return fCertid;
		}
      
		public void setCertid(int value) 
		{
         fCertid = value;
		}
      
		public String getPem() 
		{
         return fPem;
		}
      
		public void setPem(String value) 
		{
         fPem = value;
		}
      
      public X509Certificate getCert() throws java.security.cert.CertificateException
      {
         if (fCert == null) {
            ByteArrayInputStream str = new ByteArrayInputStream(fPem.getBytes());
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            fCert = (X509Certificate)cf.generateCertificate(str);
         }
         return fCert;
      }
      
		public boolean isValid() 
		{
         return fState == 1;
		}
      
		public void setValid(boolean value) 
		{
         fState = value ? 1 : 0;
		}
      
      public int getState() {
         return fState;
      }
      
      public void setState(int value) {
         fState = value;
      }
   }