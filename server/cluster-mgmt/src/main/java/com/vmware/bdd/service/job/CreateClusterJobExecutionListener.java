/***************************************************************************
 * Copyright (c) 2012-2013 VMware, Inc. All Rights Reserved.
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
package com.vmware.bdd.service.job;

import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.batch.core.JobExecution;

import com.google.gson.Gson;
import com.vmware.bdd.entity.ClusterEntity;
import com.vmware.bdd.entity.NodeEntity;
import com.vmware.bdd.exception.ClusteringServiceException;
import com.vmware.bdd.manager.ClusterManager;
import com.vmware.bdd.spectypes.HadoopRole;
import com.vmware.bdd.utils.CommonUtil;
import com.vmware.bdd.utils.Constants;

public class CreateClusterJobExecutionListener extends
      ClusterJobExecutionListener {

   private static final Logger logger = Logger
         .getLogger(CreateClusterJobExecutionListener.class);

   public void afterJob(JobExecution je) {
      super.afterJob(je);
   }

}
