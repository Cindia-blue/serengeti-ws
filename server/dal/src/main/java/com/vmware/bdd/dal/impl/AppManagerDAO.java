/***************************************************************************
 * Copyright (c) 2014 VMware, Inc. All Rights Reserved.
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

package com.vmware.bdd.dal.impl;

import java.util.List;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.vmware.bdd.dal.IAppManagerDAO;
import com.vmware.bdd.entity.AppManagerEntity;

/**
 * Author: Xiaoding Bian
 * Date: 6/4/14
 * Time: 2:55 PM
 */
@Repository
@Transactional(readOnly = true)
public class AppManagerDAO extends BaseDAO<AppManagerEntity> implements IAppManagerDAO{
   @Override
   public AppManagerEntity findByName(String name) {
      return findUniqueByCriteria(Restrictions.eq("name", name));
   }

   @Override
   public List<AppManagerEntity> findAllSortByName() {
      Order order = Order.asc("name");
      return findByCriteria(new Order[] { order }, null, null);
   }
}
