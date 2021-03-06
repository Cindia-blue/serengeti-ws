/***************************************************************************
 * Copyright (c) 2012-2015 VMware, Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/

package com.vmware.bdd.service.resmgmt.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vmware.aurora.global.Configuration;
import com.vmware.aurora.vc.VcCluster;
import com.vmware.aurora.vc.VcDatastore;
import com.vmware.aurora.vc.VcHost;
import com.vmware.aurora.vc.VcNetwork;
import com.vmware.aurora.vc.VcResourcePool;
import com.vmware.aurora.vc.vcservice.VcContext;
import com.vmware.aurora.vc.vcservice.VcSession;
import com.vmware.bdd.dal.IDatastoreDAO;
import com.vmware.bdd.dal.INetworkDAO;
import com.vmware.bdd.dal.IResourcePoolDAO;
import com.vmware.bdd.entity.NetworkEntity;
import com.vmware.bdd.entity.VcDatastoreEntity;
import com.vmware.bdd.entity.VcResourcePoolEntity;
import com.vmware.bdd.entity.resmgmt.ResourceReservation;
import com.vmware.bdd.exception.BddException;
import com.vmware.bdd.exception.VcProviderException;
import com.vmware.bdd.service.resmgmt.IResourceService;
import com.vmware.bdd.service.utils.VcResourceUtils;
import com.vmware.bdd.utils.AuAssert;
import com.vmware.bdd.utils.CommonUtil;
import com.vmware.bdd.utils.Constants;

/**
 * @author Jarred Li
 * @since 0.8
 * @version 0.8
 *
 */
@Service
public class ResourceService implements IResourceService {

   private static final Logger logger = Logger.getLogger(ResourceService.class);

   private IResourcePoolDAO rpDao;
   private IDatastoreDAO dsDao;
   private INetworkDAO networkDao;

   private final Map<UUID, ResourceReservation> reservedResource =
         new ConcurrentHashMap<UUID, ResourceReservation>();

   // Fair Semaphore ensures the policy of resource reservation is first come, first served. 
   private Semaphore reservationPermits = new Semaphore(1, true);

   /**
    * @return the rpDao
    */
   public IResourcePoolDAO getRpDao() {
      return rpDao;
   }


   /**
    * @param rpDao
    *           the rpDao to set
    */
   @Autowired
   public void setRpDao(IResourcePoolDAO rpDao) {
      this.rpDao = rpDao;
   }


   /**
    * @return the dsDao
    */
   public IDatastoreDAO getDsDao() {
      return dsDao;
   }


   /**
    * @param dsDao
    *           the dsDao to set
    */
   @Autowired
   public void setDsDao(IDatastoreDAO dsDao) {
      this.dsDao = dsDao;
   }


   /**
    * @return the networkDao
    */
   public INetworkDAO getNetworkDao() {
      return networkDao;
   }


   /**
    * @param networkDao
    *           the networkDao to set
    */
   @Autowired
   public void setNetworkDao(INetworkDAO networkDao) {
      this.networkDao = networkDao;
   }


   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getResourcePoolByName(java.lang.String)
    */
   @Override
   public VcResourcePool getResourcePoolByName(String rpName)
         throws VcProviderException {
      if (StringUtils.isEmpty(rpName)) {
         throw VcProviderException.RESOURCE_POOL_NAME_INVALID(rpName);
      }
      VcResourcePoolEntity rp = rpDao.findByName(rpName);
      final String clusterName = rp.getVcCluster();
      final String vcRPName = rp.getVcResourcePool();

      VcResourcePool vcRP =
            VcResourceUtils.findRPInVCCluster(clusterName, vcRPName);
      if (vcRP == null) {
         throw VcProviderException.RESOURCE_POOL_NOT_FOUND(rpName);
      }
      return vcRP;
   }


   @Override
   public boolean isRPExistInVc(String clusterName, String rpName)
         throws VcProviderException {
      boolean result = false;
      VcResourcePool vcRP =
            VcResourceUtils.findRPInVCCluster(clusterName, rpName);
      if (vcRP != null) {
         result = true;
      }
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getAvailableRPs()
    */
   @Override
   public List<VcResourcePool> getAvailableRPs() throws VcProviderException {
      List<VcResourcePool> result = new ArrayList<VcResourcePool>();

      List<VcResourcePoolEntity> allRPEntities =
            rpDao.findAllOrderByClusterName();

      for (VcResourcePoolEntity rpEntity : allRPEntities) {
         VcResourcePool vcRP =
               VcResourceUtils.findRPInVCCluster(rpEntity.getVcCluster(),
                     rpEntity.getVcResourcePool());
         if (vcRP != null) {
            result.add(vcRP);
         } else {
            logger.warn("Could not find the resource pool in the vCenter. RP name:"
                  + rpEntity.getName());
         }
      }
      return result;
   }

   @Override
   public void refreshResourcePool() {
      List<VcCluster> vcClusters = VcResourceUtils.getClusters();
      AuAssert.check(vcClusters != null && vcClusters.size() != 0);
      for (VcCluster vcCluster : vcClusters) {
         VcResourceUtils.refreshResourcePool(vcCluster);
      }
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getDatastoreByName(java.lang.String)
    */
   @Override
   public List<VcDatastore> getDatastoreByName(String dsName)
         throws VcProviderException {
      logger.info("get datastore by name: " + dsName);
      List<VcDatastoreEntity> datastores = dsDao.findByName(dsName);
      return findDSInVC(datastores);
   }

   @Deprecated
   public void refreshVcResources() {
      VcResourceUtils.refreshVcResources();
   }

   @Override
   public void refreshDatastore() {
      List<VcCluster> vcClusters = VcResourceUtils.getClusters();
      AuAssert.check(vcClusters != null && vcClusters.size() != 0);
      for (VcCluster vcCluster : vcClusters) {
         VcResourceUtils.refreshDatastore(vcCluster);
      }
   }

   @Override
   public boolean isDatastoreExistInVC(String dsName)
         throws VcProviderException {
      boolean result = false;
      Collection<VcDatastore> vcDS =
            VcResourceUtils.findDSInVCByPattern(dsName);
      if (vcDS != null && vcDS.size() > 0) {
         result = true;
      }
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getAvailableDSs()
    */
   @Override
   public List<VcDatastore> getAvailableDSs() throws VcProviderException {
      List<VcDatastoreEntity> datastores = dsDao.findAllSortByName();
      return findDSInVC(datastores);
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.IResourceManager#isDatastoreAccessibleByCluster(java.lang.String, java.lang.String)
    */
   @Override
   public boolean isDatastoreAccessibleByCluster(String datastoreSpec,
         String cluster) throws VcProviderException {
      boolean result = false;
      List<VcDatastore> availableDatastores = getDatastoreByName(datastoreSpec);
      List<VcHost> hosts = getHostsByClusterName(cluster);
      for (VcHost host : hosts) {
         List<VcDatastore> hostDSs = host.getDatastores();
         boolean found = false;
         for (VcDatastore availableDS : availableDatastores) {
            for (VcDatastore hostDS : hostDSs) {
               if (availableDS.getName().equals(hostDS.getName())) {
                  found = true;
                  break;
               }
            }
            if (found) {
               break;
            }
         }
         if (found) {
            result = true;
            break;
         }
      }
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getNetworkByName(java.lang.String)
    */
   @Override
   public VcNetwork getNetworkByName(String networkName)
         throws VcProviderException {
      NetworkEntity networkEntity = networkDao.findNetworkByName(networkName);
      return VcResourceUtils.findNetworkInVC(networkEntity.getPortGroup());
   }

   @Override
   public void refreshNetwork() {
      List<VcCluster> vcClusters = VcResourceUtils.getClusters();
      AuAssert.check(vcClusters != null && vcClusters.size() != 0);
      for (VcCluster vcCluster : vcClusters) {
         VcResourceUtils.refreshNetwork(vcCluster);
      }
   }

   @Override
   public boolean isNetworkExistInVc(String networkName)
         throws VcProviderException {
      boolean result = false;
      refreshNetwork();
      VcNetwork network = VcResourceUtils.findNetworkInVC(networkName);
      if (network != null) {
         result = true;
      }
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getAvailableNetworks()
    */
   @Override
   public List<VcNetwork> getAvailableNetworks() throws VcProviderException {
      List<NetworkEntity> networkList = networkDao.findAllNetworks();
      List<VcNetwork> result = new ArrayList<VcNetwork>();
      for (NetworkEntity networkEntity : networkList) {
         VcNetwork network =
               VcResourceUtils.findNetworkInVC(networkEntity.getPortGroup());
         if (network != null) {
            result.add(network);
         } else {
            logger.warn("Could not find the network in the vCenter. Network name: "
                  + networkEntity.getName());
         }
      }
      return result;
   }

   @Override
   public boolean isNetworkSharedInCluster(String networkName,
         String clusterName) throws VcProviderException {
      boolean result = true;
      refreshNetwork();
      VcNetwork vcNetwork = getNetworkByName(networkName);
      if (vcNetwork == null) {
         return false;
      } else {
         String portGroupName = vcNetwork.getName();
         List<VcHost> hosts = getHostsByClusterName(clusterName);
         for (VcHost vcHost : hosts) {
            List<VcNetwork> networks = vcHost.getNetworks();
            boolean found = false;
            for (VcNetwork network : networks) {
               if (network.getName().equals(portGroupName)) {
                  found = true;
                  break;
               }
            }
            if (!found) {
               logger.error("host " + vcHost + " has networks " + networks
                     + " does not have target network " + portGroupName);
               result = false;
               break;
            }
         }
      }
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getHostsByRpName(java.lang.String)
    */
   @Override
   public List<VcHost> getHostsByRpName(String rpName)
         throws VcProviderException {
      VcResourcePoolEntity rp = rpDao.findByName(rpName);
      final String vcClusterName = rp.getVcCluster();
      final String vcRpName = rp.getVcResourcePool();
      return VcResourceUtils.findAllHostInVcResourcePool(vcClusterName, vcRpName);
   }

   private UUID addReservation(ResourceReservation resReservation) {
      UUID result = UUID.randomUUID();
      reservedResource.put(result, resReservation);
      return result;
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#reserveResoruce(com.vmware.bdd.bo.ResourceReservation)
    */
   @Override
   public UUID reserveResource(ResourceReservation resReservation)
         throws VcProviderException {
      boolean concurrentJobEnabled = Configuration.getBoolean(Constants.SERENGETI_CONCURRENT_JOB_ENABLED, false);
      if(concurrentJobEnabled) {//if concurrent creation switch is on, don't check concurrent creations.
         logger.info("concurrent cluster create is performed.");
         return addReservation(resReservation);
      } else { // Only one creation can be running, others will wait for its finishing.
         long maxWait = Configuration.getLong("serengeti.concurrent.job.maxWaitMins", 120l);
         try {
            reservationPermits.tryAcquire(maxWait, TimeUnit.MINUTES);
         } catch (InterruptedException e) {
            BddException.INTERNAL(e, "waiting for concurrent cluster creation but was interrupted");
         }
         logger.info("reservedResource is empty: " + reservedResource.isEmpty());

         if(resourceAlreadyReserved()) {
            ResourceReservation[] reservations = reservedResource.values().toArray(new ResourceReservation[0]);
            String clusterName = reservations[0].getClusterName();
            logger.error("concurrent cluster create is not allowed.");
            throw VcProviderException.CONCURRENT_CLUSTER_CREATING(clusterName != null ? clusterName : "unknown cluster");
         } else {
            return addReservation(resReservation);
         }
      }
   }

   private boolean resourceAlreadyReserved() {
      return !reservedResource.isEmpty();
   }

   private void removeReservation(UUID reservationId) {
      if(reservedResource.containsKey(reservationId)) {
         reservedResource.remove(reservationId);
         if(!Configuration.getBoolean(Constants.SERENGETI_CONCURRENT_JOB_ENABLED, false)) {
            reservationPermits.release();
         }
      } else {
         BddException.INTERNAL(new Exception(), String.format("%s: reservation ID %s not found", Thread.currentThread().getName(), reservationId));
      }
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#cancleReservation(long)
    */
   @Override
   public void cancleReservation(UUID reservationId)
         throws VcProviderException {
      removeReservation(reservationId);
      logger.info("current VMs Cloning is canceled, remove Reservation.");
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#commitReservation(long)
    */
   @Override
   public void commitReservation(UUID reservationId)
         throws VcProviderException {
      removeReservation(reservationId);
      logger.info("current VMs Cloning is done, commit Reservation.");
   }

   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getHostsByClusterName(java.lang.String)
    */
   @Override
   public List<VcHost> getHostsByClusterName(String clusterName)
         throws VcProviderException {
      return VcResourceUtils.findAllHostsInVCCluster(clusterName);
   }


   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.ResourceManager#getAvailableClusters()
    */
   @Override
   public List<VcCluster> getAvailableClusters() throws VcProviderException {
      final HashSet<String> clusterNameFromRps = new HashSet<>(rpDao.findAllClusterName());

      return VcContext.inVcSessionDo(new VcSession<List<VcCluster>>() {

         @Override
         protected List<VcCluster> body() throws Exception {
            List<VcCluster> results = new ArrayList<>();

            for (VcCluster vcCluster : VcResourceUtils.getClusters()) {
               if (clusterNameFromRps.contains(vcCluster.getName())) {
                  results.add(vcCluster);
               }
            }

            return results;
         }

      });
   }


   /**
    * @param datastores
    * @return
    */
   protected List<VcDatastore> findDSInVC(List<VcDatastoreEntity> datastores) {
      List<VcDatastore> result = new ArrayList<VcDatastore>();
      if (datastores == null) {
         return result;
      }
      for (VcDatastoreEntity dsEntity : datastores) {
         Collection<VcDatastore> vcDS;
         if (dsEntity.getRegex() != null && dsEntity.getRegex()) {
            vcDS = VcResourceUtils.findDSInVCByPattern(dsEntity.getVcDatastore());
         } else {
            vcDS = VcResourceUtils.findDSInVCByPattern(
                  CommonUtil.getDatastoreJavaPattern(dsEntity.getVcDatastore()));
         }
         if (vcDS != null) {
            result.addAll(vcDS);
         } else {
            logger.warn("Could not find the data store in the vCenter. Name:"
                  + dsEntity.getVcDatastore());
         }
      }
      return result;
   }


   @Override
   public Map<String, String> getCloudProviderAttributes() {
      Map<String, String> attr = new HashMap<String, String>();
      attr.put(Constants.CLOUD_PROVIDER_NAME_FIELD,
            Constants.VSPHERE_PROVIDER_NAME);
      attr.put(Constants.CLOUD_PROVIDER_TYPE_FIELD,
            Constants.VSPHERE_PROVIDER_TYPE);
      return attr;
   }


   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.IResourceService#filterHostsByNetwork(java.util.List, java.util.List)
    */
   @Override
   public List<String> filterHostsByNetwork(List<String> networkList,
         List<com.vmware.bdd.spectypes.VcCluster> clusters) {
      // refresh all resource when at beginning of creating/resizing/resuming cluster
      //refreshNetwork();
      Set<String> networkNames = new HashSet<String>();
      networkNames.addAll(networkList);
      Set<String> portGroupNames = new HashSet<String>();
      for (String networkName : networkNames) {
         VcNetwork vcNetwork = getNetworkByName(networkName);
         if (vcNetwork == null) {
            throw VcProviderException.NETWORK_NOT_FOUND(networkName);
         }
         portGroupNames.add(vcNetwork.getName());
      }

      List<String> noNetworkHosts = new ArrayList<String>();
      for (com.vmware.bdd.spectypes.VcCluster cluster : clusters) {
         List<VcHost> hosts = getHostsByClusterName(cluster.getName());
         for (VcHost vcHost : hosts) {
            // check each host has all the networks
            List<VcNetwork> vcNetworks = vcHost.getNetworks();
            List<String> hostNetworkNames = new ArrayList<String>();
            for (VcNetwork vcNetwork : vcNetworks) {
               hostNetworkNames.add(vcNetwork.getName());
            }
            if (!hostNetworkNames.containsAll(portGroupNames)) {
               logger.info("host" + vcHost.getName() + " has networks " + vcNetworks
                     + " does not have target network " + portGroupNames);
               noNetworkHosts.add(vcHost.getName());
            }
         }
      }
      return noNetworkHosts;
   }


   /* (non-Javadoc)
    * @see com.vmware.bdd.service.resmgmt.IResourceService#isNetworkAccessibleByCluster(java.util.List, java.util.List)
    */
   @Override
   public boolean isNetworkAccessibleByCluster(List<String> networkList,
         List<com.vmware.bdd.spectypes.VcCluster> clusters) {
      // refresh all resource when at beginning of creating/resizing/resuming cluster
//      refreshNetwork();

      Set<String> portGroupNames = new HashSet<String>();
      for (String networkName : networkList) {
         VcNetwork vcNetwork = getNetworkByName(networkName);
         if (vcNetwork == null) {
            throw VcProviderException.NETWORK_NOT_FOUND(networkName);
         }
         portGroupNames.add(vcNetwork.getName());
      }

      for (com.vmware.bdd.spectypes.VcCluster cluster : clusters) {
         List<VcHost> hosts = getHostsByClusterName(cluster.getName());
         for (VcHost vcHost : hosts) {
            List<VcNetwork> vcNetworks = vcHost.getNetworks();
            List<String> hostNetworkNames = new ArrayList<String>();
            for (VcNetwork vcNetwork : vcNetworks) {
               hostNetworkNames.add(vcNetwork.getName());
            }
            if (hostNetworkNames.containsAll(portGroupNames)) {
               return true;
            }
         }
      }
      return false;
   }

}
