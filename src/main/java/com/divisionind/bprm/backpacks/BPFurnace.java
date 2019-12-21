/*
 * BackpacksRemastered - remastered version of the popular Backpacks plugin
 * Copyright (C) 2019, Andrew Howard, <divisionind.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.divisionind.bprm.backpacks;

import com.divisionind.bprm.BackpackHandler;
import com.divisionind.bprm.PotentialBackpackItem;
import com.divisionind.bprm.UpdateItemCallback;
import com.divisionind.bprm.VirtualFurnace;
import com.divisionind.bprm.events.BackpackFurnaceTickEvent;
import com.divisionind.bprm.nms.NBTMap;
import com.divisionind.bprm.nms.reflect.NBTType;
import com.divisionind.bprm.nms.reflect.NMS;
import com.divisionind.bprm.nms.reflect.NMSClass;
import com.divisionind.bprm.nms.reflect.NMSMethod;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

public class BPFurnace extends BackpackHandler {

    // TODO if the player is wearing the backpack (and furnacing), make it emit light around them (later update) (also, yes, this is possible, see mc protocol wiki)

    @Override
    public Inventory openBackpack(Player p, PotentialBackpackItem backpack) throws Exception {
        Object furnace = null;
        if (backpack.hasNBT("furnace_id")) {
            UUID id = UUID.fromString((String) backpack.getNBT(NBTType.STRING, "furnace_id"));
            VirtualFurnace vFern = BackpackFurnaceTickEvent.VIRTUAL_FURNACES.get(id);

            if (vFern != null) {
                // virtual furnace identified
                furnace = vFern.getFurnace();
                vFern.setReleased(false);
            }
        }

        // contains the magic that makes our virtual furnace
        if (furnace == null) {
            // create fake furnace tile entity
            // furnace = new TileEntityFurnaceFurnace();
            furnace = NMSClass.TileEntityFurnaceFurnace.getClazz().getDeclaredConstructor().newInstance();
            // assign it a dimension
            //furnace.setWorld(((CraftServer)Bukkit.getServer()).getServer().getWorldServer(DimensionManager.OVERWORLD));
            Object craftServer = NMSClass.CraftServer.getClazz().cast(Bukkit.getServer());
            Object dedicatedServer = NMSMethod.getServer.getMethod().invoke(craftServer);
            Object worldServer = NMSMethod.getWorldServer.getMethod().invoke(dedicatedServer, NMS.DIMENSION_MANAGER_OVERWORLD);
            NMSMethod.setWorld.getMethod().invoke(furnace, worldServer);

            if (backpack.hasNBT("furnace_data")) {
                //furnace.load((NBTTagCompound) backpack.getAsMap("furnace_data").getTagCompound());
                NMSMethod.load.getMethod().invoke(furnace, backpack.getAsMap("furnace_data").getTagCompound());
            }

            UUID furnaceId = UUID.randomUUID();
            BackpackFurnaceTickEvent.VIRTUAL_FURNACES.put(furnaceId, new VirtualFurnace(furnace));
        }


        // create an inventory to represent the furnace
        //new CraftInventoryFurnace(furnace);
        return (Inventory) NMSClass.CraftInventoryFurnace.getClazz().getDeclaredConstructor(NMSClass.TileEntityFurnace.getClazz()).newInstance(furnace);
    }

    @Override
    public void onClose(InventoryCloseEvent e, PotentialBackpackItem backpack, UpdateItemCallback callback) throws Exception {
        Inventory inv = e.getInventory();
        //TileEntityFurnace furnace = (TileEntityFurnace) ((CraftInventory)inv).getInventory();
        Object craftInventory = NMSClass.CraftInventory.getClazz().cast(inv);
        Object iInventory = NMSMethod.getInventory.getMethod().invoke(craftInventory);
        Object furnace = NMSClass.TileEntityFurnace.getClazz().cast(iInventory);

        // looks up virtual furnace from table
        Map.Entry<UUID, VirtualFurnace> vFurnaceEntry = locateVirtualFurnace(furnace);
        if (vFurnaceEntry != null) {
            backpack.setNBT(NBTType.STRING, "furnace_id", vFurnaceEntry.getKey().toString());
            vFurnaceEntry.getValue().setReleased(true);
        }

        // update backpack contents with inventory
        NBTMap nbtMap = new NBTMap();
        //furnace.save((NBTTagCompound) nbtMap.getTagCompound());
        NMSMethod.save.getMethod().invoke(furnace, nbtMap.getTagCompound());
        backpack.setAsMap("furnace_data", nbtMap);

        // ((CraftInventory)inv).getInventory() == IInventory which is our instance of TileEntityFurnace, we use this for identifying

        // update item post NBT modification
        callback.update(backpack.getModifiedItem());
    }

    public static Map.Entry<UUID, VirtualFurnace> locateVirtualFurnace(Object furnace) {
        for (Map.Entry<UUID, VirtualFurnace> entry : BackpackFurnaceTickEvent.VIRTUAL_FURNACES.entrySet()) {
            if (entry.getValue().getFurnace() == furnace) {
                return entry;
            }
        }

        return null;
    }
}

// ** BASED OFF OF Bukkit.createInventory(null, InventoryType.FURNACE);
// ** add support for more from: org.bukkit.craftbukkit.vX_XX_RX.inventory.util.CraftInventoryCreator
//public static class Furnace extends CraftTileInventoryConverter {
//    public Furnace() {
//    }
//
//    public IInventory getTileEntity() {
//        TileEntityFurnace furnace = new TileEntityFurnaceFurnace();
//        furnace.setWorld(MinecraftServer.getServer().getWorldServer(DimensionManager.OVERWORLD));
//        return furnace;
//    }
//
//    public Inventory createInventory(InventoryHolder owner, InventoryType type, String title) {
//        IInventory tileEntity = this.getTileEntity();
//        ((TileEntityFurnace)tileEntity).setCustomName(CraftChatMessage.fromStringOrNull(title));
//        return this.getInventory(tileEntity);
//    }
//
//    public Inventory getInventory(IInventory tileEntity) {
//        return new CraftInventoryFurnace((TileEntityFurnace)tileEntity);
//    }
//}