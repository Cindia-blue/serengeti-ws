package com.vmware.bdd.service;

import java.util.ArrayList;
import java.util.List;

import com.vmware.bdd.entity.NodeEntity;

public interface ISetPasswordService {
   /**
    * Set password for nodes in cluster
    *
    * @param clusterName
    * @param nodes
    * @param password
    * @return failed nodes list
    */
   public ArrayList<String> setPasswordForNodes(String clusterName, List<NodeEntity> nodes, String password);

   /**
    * Set password for node in cluster
    *
    * @param clusterName
    * @param fixedNodeIP
    * @param newPassword
    * @return success or not
    * @throws Exception
    */
   public boolean setPasswordForNode(String clusterName, NodeEntity node, String newPassword) throws Exception;
}
