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

package com.vmware.aurora.vc.vcevent;

import java.util.EnumSet;

import com.vmware.aurora.util.AuAssert;
import com.vmware.aurora.vc.VcCache;
import com.vmware.aurora.vc.VcVirtualMachine;
import com.vmware.aurora.vc.vcevent.VcEventHandlers.IVcEventHandler;
import com.vmware.aurora.vc.vcevent.VcEventHandlers.VcEventType;
import com.vmware.vim.binding.vim.event.Event;
import com.vmware.vim.binding.vim.event.EventEx;
import com.vmware.vim.binding.vim.event.ResourcePoolEvent;
import com.vmware.vim.binding.vim.event.VmCreatedEvent;
import com.vmware.vim.binding.vim.event.VmDisconnectedEvent;
import com.vmware.vim.binding.vim.event.VmEvent;
import com.vmware.vim.binding.vim.event.VmResourcePoolMovedEvent;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;

public class VcEventRouter {
   private static final EnumSet<VcEventType> vmEvents = EnumSet.of(
         VcEventType.VmConfigMissing, VcEventType.VmConnected,
         VcEventType.VmCreated, VcEventType.VmDasBeingReset,
         VcEventType.VmDasResetFailed, VcEventType.VmDisconnected,
         VcEventType.VmMessage, VcEventType.VmMessageError,
         VcEventType.VmMessageWarning, VcEventType.VmOrphaned,
         VcEventType.VmPoweredOn, VcEventType.VmPoweredOff,
         VcEventType.VmReconfigured, VcEventType.VmRelocated,
         VcEventType.VmRemoved, VcEventType.VmRenamed,
         VcEventType.VmResourcePoolMoved, VcEventType.VmResuming,
         VcEventType.VmSuspended, VcEventType.VmAppHealthChanged,
         VcEventType.NotEnoughResourcesToStartVmEvent,
         VcEventType.VmMaxRestartCountReached, VcEventType.VmFailoverFailed);

   /* External rp events to monitor. */
   private static final EnumSet<VcEventType> rpEvents = EnumSet.of(
   /* Generated by pseudo-tasks. */
   VcEventType.ResourcePoolCreated, VcEventType.ResourcePoolMoved,
         VcEventType.ResourcePoolReconfigured,

         /* Generated by real vc tasks. */
         VcEventType.ResourcePoolDestroyed, VcEventType.ResourceViolated);

   protected static final String ResourcePoolEvent = null;

   public VcEventRouter() {
      /* High level handler for external vm events. */
      VcEventListener.installExtEventHandler(vmEvents, new IVcEventHandler() {
         @Override
         public boolean eventHandler(VcEventType type, Event e)
               throws Exception {
            // Event can be either VmEvent or EventEx (TODO: Explicitly check for
            // VM specific EventEx class usage? e.g. VcEventType.VmAppHealthChanged?)
            AuAssert.check(e instanceof VmEvent || e instanceof EventEx);
            ManagedObjectReference moRef = e.getVm().getVm();
            switch (type) {
            case VmRemoved: {
               VcCache.purge(moRef);
               ManagedObjectReference rpMoRef = VcCache.removeVmRpPair(moRef);
               if (rpMoRef != null) {
                  VcCache.refresh(rpMoRef);
               }
               return false;
            }
            case VmDisconnected:
            case VmConnected: {
               VcVirtualMachine vm = VcCache.getIgnoreMissing(moRef);
               if (vm == null) {
                  return false;
               }
               vm.update();
            }
            case VmResourcePoolMoved: {
               VmResourcePoolMovedEvent event = (VmResourcePoolMovedEvent) e;
               VcCache.refresh(event.getOldParent().getResourcePool());
               VcCache.refresh(event.getNewParent().getResourcePool());
               break;
            }
            case VmCreated: {
               VmCreatedEvent event = (VmCreatedEvent) e;
               VcVirtualMachine vm = VcCache.get(event.getVm().getVm());
               vm.refreshRP();
               break;
            }
            }
            VcCache.refreshAll(moRef);
            return false;
         }
      });

      /* High level handler for external rp events. */
      VcEventListener.installExtEventHandler(rpEvents, new IVcEventHandler() {
         @Override
         public boolean eventHandler(VcEventType type, Event e)
               throws Exception {
            ManagedObjectReference moRef =
                  ((ResourcePoolEvent) e).getResourcePool().getResourcePool();
            if (type == VcEventType.ResourcePoolDestroyed) {
               VcCache.purge(moRef);
            } else {
               VcCache.refreshAll(moRef);
            }
            return false;
         }
      });
   }
}
