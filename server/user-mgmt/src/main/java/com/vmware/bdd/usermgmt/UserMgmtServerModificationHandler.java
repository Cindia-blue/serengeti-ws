/******************************************************************************
 *   Copyright (c) 2014 VMware, Inc. All Rights Reserved.
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *****************************************************************************/
package com.vmware.bdd.usermgmt;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.vmware.bdd.apitypes.UserMgmtServer;

/**
 * Created By xiaoliangl on 12/24/14.
 */
@Component
public class UserMgmtServerModificationHandler {
   private final static Logger LOGGER = Logger.getLogger(UserMgmtServerModificationHandler.class);

   @Autowired
   private MgmtVmCfgService mgmtVmCfgService;

   public void onModification(UserMgmtServer userMgmtServer) {
      //reconfig mgmt vm
      mgmtVmCfgService.reconfigUserMgmtServer(userMgmtServer);

      //@TODO reconfigure cluster VMs
   }
}