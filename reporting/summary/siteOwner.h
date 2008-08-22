const char *siteOwners[] = {
"Alliance","CMS",
"AGLT2","USATLAS",
"ASGC_OSG","CSC",
"bandera","CSC",
"BNL_LOCAL","USATLAS",
"BNL_OSG","USATLAS",
"BNL_ATLAS_1","USATLAS",
"BNL_ATLAS_2","USATLAS",
"BNL_ITB_Test1","USATLAS",
"BU_ATLAS_Tier2","USATLAS",
"cinvestav","DOSAR",
"CIT_CMS_T2","CMS",
"CIT_CMS_T2:srm_v1","CMS",
"CIT_ITB_1","CMS",
"CIT_ITB_2","CMS",
"Clemson","nanoHUB Support Center",
"Clemson-IT","ENGAGE",
"CMS-BURT-ITB","CMS",
"DARTMOUTH","CSC",
"FERMIGRID_DCACHE_SE","Fermilab",
"FIU-PG","fGOC",
"FNAL_CDFOSG_1","CDF",
"FNAL_CDFOSG_2","CDF",
"FNAL_CDFOSG_3","CDF",
"FNAL_CDFOSG_4","CDF",
"FNAL_CDFOSG_5","CDF",
"FNAL_DZEROOSG_1","DZERO",
"FNAL_DZEROOSG_2","DZERO",
"FNAL_DZEROOSG_3","DZERO",
"FNAL_FERMIGRID","Fermilab",
"FNAL_FERMIGRID_TEST","FERMILAB",
"FNAL_GPFARM","Fermilab",
"FNAL_GPFARM_TEST","Fermilab",
"FNAL_GPGRID_1","Fermilab",
"FNAL_GPGRID_2","Fermilab",
"FSU-HEP","CMS",
"GLOW","CMS",
"GRASE-ALBANY-NYS","GRASE",
"GRASE-BINGHAMTON","GRASE",
"GRASE-CCR-U2","GRASE",
"GRASE-CORNELL-CTCNYSGRID","GRASE",
"GRASE-GENESEO-OSG","GRASE",
"GRASE-HWI-IDUN","GRASE",
"GRASE-MARIST-nysgrid11","GRASE",
"GRASE-NU-CARTMAN","GRASE",
"GRASE-NYU-BENCH","GRASE",
"GRASE-RIT-GCLUSTER","GRASE",
"GRASE-SB-sbnysgrid.cs.sunysb.edu","GRASE",
"Syracuse","GRASE",
"GRASE-UR-NEBULA","GRASE",
"GROW-ITB","grow",
"GROW-PROD","GROW-GOC",
"GROW-UNI-P","grow-goc",
"HAMPTONU","USATLAS",
"HEPGRID_UERJ","CMS",
"IPAS_OSG","CSC",
"isuhep","DOSAR",
"ITB_INSTALL_TEST","CMS",
"ITB_INSTALL_TEST_2","CMS",
"ITB_INSTALL_TEST_3","CMS",
"IUB-VTB","CSC",
"IUB_ITB","",
"IUPUI-ITB","OSG-GOC",
"IU_ATLAS_Tier2","USATLAS",
"IU_BANDICOOT","USATLAS",
"IU_iuatlas","OSG-GOC",
"IU_OSG","USATLAS",
"Lehigh_coral","GLOW",
"LIGO-CIT-ITB","LIGO",
"LIGO-CIT-VTB","LIGO",
"LTU_CCT","DOSAR",
"LTU_OSG","DOSAR",
"MCGILL_HEP","CDF",
"MIT_CMS","CMS",
"MIT_CMS:srm_v1","CMS",
"MSU-OSG","Unknown",
"MWT2_IU","USATLAS",
"MWT2_UC","USATLAS",
"Nebraska","CMS",
"NERSC-Davinci","NERSC",
"NERSC-ITB","NERSC",
"NERSC-Jacquard","NERSC",
"NERSC-PDSF","NERSC",
"NERSC-STAR","STAR",
"NERSC-STAR-DRM","STAR",
"NERSC-VM-VTB0","NERSC",
"NYSGRID-CCR-U2","NYSGRID",
"NYSGRID_CORNELL_NYS1","NYSGRID",
"NWICG_NotreDame","NYSGRID",
"ORNL_NSTG","ORNL",
"OSG_INSTALL_TEST_2","CSC",
"OSG_ITB_PSU","LIGO",
"OSG_LIGO_PSU","LIGO",
"OUHEP_ITB","USATLAS",
"OUHEP_OSG","USATLAS",
"OU_OCHEP_SWT2","USATLAS",
"OU_OSCER_ATLAS","USATLAS",
"OU_OSCER_CONDOR","DZERO",
"OU_OSCER_OSG","USATLAS",
"PROD_SLAC","USATLAS",
"Purdue-ITB","CSC",
"Purdue-Lear","CMS",
"Purdue-Physics","CSC",
"Purdue-RCAC","CMS",
"Rice","CSC",
"SBGrid-Harvard-East","SBGrid",
"SBGrid-Harvard-Exp","SBGrid",
"SDSS_TAM","SDSS",
"SMU_PHY","USATLAS",
"SPRACE","CMS",
"SPRACE-SE","CMS",
"STAR-Bham","STAR",
"STAR-BNL","STAR",
"STAR-SAO_PAULO","STAR",
"STAR-WSU","STAR",
"SWT2_CPB","USATLAS",
"T2_Nebraska_Storage","CMS",
"TACC","TACC",
"TestSite","OSG-GOC",
"TTU-ANTAEUS","CSC",
"TTU-TESTWULF","CMS",
"UARK_ACE","GPN",
"UCSDT2","CMS",
"UCSandiegoOSG-Prod-SE","CMS",
"UCSanDiegoPG","CMS",
"UC_ATLAS_MWT2","USATLAS",
"UC_ITB_TEST1","UC CI",
"UC_T2DEV_ITB","USATLAS",
"UC_Teraport","CSC",
"UCR-HEP","Generic",
"UERJ_HEPGRID","CSC",
"UF-HPC","fGOC",
"UFlorida-EO","fGOC",
"UFlorida-HPC","CMS",
"UFlorida-IGT","fGOC",
"UFlorida-IHEPA","fGOC",
"UFlorida-PG","CMS",
"UFlorida-PG:srm_v1","CMS",
"UIC_PHYSICS","STAR",
"UIOWA-OSG-ITB","GROW-GOC",
"UIOWA-OSG-PROD","GROW-GOC",
"UIUC-HEP","USATLAS",
"UMATLAS","USATLAS",
"UmissHEP","Unknown",
"Nebraska Tier 2 Center","CMS",
"UNM_HPC","CSC",
"USATLAS_dCache_at_BNL","USATLAS",
"USCMS-FNAL-WC1-CE","CMS",
"USCMS-FNAL-WC1-SE","CMS",
"USCMS-FNAL-WC1-SE-ITB","CMS",
"UTA_DPCC","USATLAS",
"UTA_SWT2","USATLAS",
"UTenn_CMS","CMS",
"UVA-HEP","CMS",
"UVA-sunfire","CSC",
"UWMadisonCMS","CMS",
"UWMadisonCMS-SE","CMS",
"UWMilwaukee","LIGO",
"VAMPIRE-Vanderbilt","CSC"
};
