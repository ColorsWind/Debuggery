/*
 * This file is part of Debuggery.
 *
 * Debuggery is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Debuggery is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Debuggery.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.destroystokyo.debuggery.reflection.formatters;

import com.destroystokyo.debuggery.util.PlatformUtil;
import org.apache.commons.lang.NotImplementedException;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles formatting the arguments we send to method invocation
 */
public class InputFormatter {

    /**
     * Takes a given list of strings and instantiates relevant objects based on the corresponding classes
     * given to this function.
     *
     * @param classes class types the string arguments should correspond to
     * @param input   list of input strings corresponding to the relevant class type
     * @param sender  entity to use for shortcuts like "here", "there", etc.
     * @return array of object instances pertaining to the input types
     * @throws InputException when there's an issue determining the proper response for a given input
     */
    @Nonnull
    public static Object[] getTypesFromInput(Class[] classes, List<String> input, @Nullable CommandSender sender) throws InputException {
        if (input.size() == 0) {
            return new Object[0];
        }

        List<Object> out = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            out.add(getTypeForClass(classes[i], input.get(i), sender));
        }

        return out.toArray();
    }

    @Nullable
    private static Object getTypeForClass(Class clazz, String input, @Nullable CommandSender sender) throws InputException {
        if (clazz == null) {
            throw new IllegalArgumentException("Cannot determine input type for null class");
        }

        if (clazz.equals(String.class)) {
            return input;
        } else if (clazz.isPrimitive()) {
            return getPrimitive(clazz, input);
        } else if (clazz.equals(Class.class)) {
            return getBukkitClass(input);
        } else if (clazz.equals(Material.class)) {
            return getMaterial(input);
        } else if (clazz.equals(MaterialData.class)) {
            return getMaterialData(input);
        } else if (clazz.equals(Location.class)) {
            return getLocation(input, sender);
        } else if (clazz.equals(Entity.class)) {
            return getEntity(input, sender);
        } else if (clazz.equals(UUID.class)) {
            return getUUID(input, sender);
        } else if (clazz.equals(GameMode.class)) {
            return getGameMode(input);
        } else if (clazz.equals(Difficulty.class)) {
            return getDifficulty(input);
        } else if (Enum.class.isAssignableFrom(clazz)) { // Do not use for all enum types, lacks magic value support
            return getValueFromEnum(clazz, input);
        } else if (clazz.equals(ItemStack.class)) {
            return getItemStack(input, sender);
        } else if (clazz.equals(Class[].class)) {
            return getBukkitClasses(input);
        }

        throw new InputException(new NotImplementedException("Input handling for class type " + clazz.getSimpleName() + " not implemented yet"));
    }

    @Nonnull
    private static UUID getUUID(String input, CommandSender sender) throws InputException {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ex) {
            if (sender instanceof Player) {
                Entity entity = getEntity(input, sender);

                if (entity != null) {
                    return entity.getUniqueId();
                }
            }

            throw new InputException(ex);
        }
    }

    @Nonnull
    private static Object getPrimitive(Class clazz, String input) throws InputException {
        try {
            if (input == null) {
                throw new NumberFormatException();
            }

            if (clazz.equals(byte.class)) {
                return Byte.parseByte(input);
            } else if (clazz.equals(short.class)) {
                return Short.parseShort(input);
            } else if (clazz.equals(int.class)) {
                return Integer.parseInt(input);
            } else if (clazz.equals(long.class)) {
                return Long.parseLong(input);
            } else if (clazz.equals(float.class)) {
                return Float.parseFloat(input);
            } else if (clazz.equals(double.class)) {
                return Double.parseDouble(input);
            } else if (clazz.equals(boolean.class)) {
                return Boolean.parseBoolean(input);
            } else if (clazz.equals(char.class)) {
                return input.charAt(0);
            }
        } catch (NumberFormatException ex) {
            throw new InputException(ex);
        }

        throw new AssertionError("Java added another primitive type!");
    }

    @Nonnull
    private static <T extends Enum<T>> T getValueFromEnum(Class clazz, String input) throws InputException {
        try {
            //noinspection unchecked
            return Enum.valueOf((Class<T>) clazz, input.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InputException(ex);
        }
    }

    @Nonnull
    private static Material getMaterial(String input) {
        return Material.matchMaterial(input);
    }

    @Nonnull
    private static ItemStack getItemStack(String input, CommandSender sender) {
        if (sender instanceof Player) {
            if (input.equalsIgnoreCase("this")) {
                return ((Player) sender).getInventory().getItemInMainHand();
            }
        }
        return new ItemStack(getMaterial(input));
    }

    @Nonnull
    private static MaterialData getMaterialData(String input) throws InputException {
        String[] contents = input.split(":", 2);
        Material material = getMaterial(contents[0]);
        byte data = (byte) getPrimitive(byte.class, contents[1]);

        //noinspection deprecation
        return new MaterialData(material, data);
    }

    @Nonnull
    private static Location getLocation(String input, CommandSender sender) throws InputException {
        if (sender instanceof Player) {
            final Player player = (Player) sender;
            if (input.equalsIgnoreCase("here")) {
                return player.getLocation();
            } else if (input.equalsIgnoreCase("there")) {
                return player.getTargetBlock(null, 50).getLocation();
            }
        }

        String[] contents = input.split(",", 4);
        double[] xyz = new double[3];

        World world = Bukkit.getWorld(contents[0]);
        if (world == null) {
            throw new InputException(new NullPointerException("No world by that name could be found"));
        }

        try {
            for (int i = 0; i < contents.length - 1; i++) {
                xyz[i] = Double.parseDouble(contents[i + 1]); // offset by 1 because of world at index 0
            }
        } catch (NumberFormatException ex) {
            throw new InputException(ex);
        }

        return new Location(world, xyz[0], xyz[1], xyz[2]);
    }

    @Nonnull
    private static GameMode getGameMode(String input) throws InputException {
        try {
            int val = Integer.parseInt(input);
            //noinspection deprecation
            return GameMode.getByValue(val);
        } catch (NumberFormatException ignored) {
        }

        return getValueFromEnum(GameMode.class, input);
    }

    @Nonnull
    private static Difficulty getDifficulty(String input) throws InputException {
        try {
            int val = Integer.parseInt(input);
            //noinspection deprecation
            return Difficulty.getByValue(val);
        } catch (NumberFormatException ignored) {
        }

        return getValueFromEnum(Difficulty.class, input);
    }

    @Nonnull
    private static Class[] getBukkitClasses(String input) throws InputException {
        List<Class> classList = new ArrayList<>();
        String[] classNames = input.split(",");

        for (String className : classNames) {
            classList.add(getBukkitClass(className));
        }

        return classList.toArray(new Class[0]);
    }

    @Nonnull
    private static Class getBukkitClass(String input) throws InputException {
        // This is only used for entities right now, so we can save some drama and just search those packages
        final String[] searchPackages = {"org.bukkit.entity", "org.bukkit.entity.minecart"};
        String normalized = Character.toUpperCase(input.charAt(0)) + input.substring(1).toLowerCase();
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }

        Class clazz = null;
        for (String packageName : searchPackages) {
            try {
                clazz = Class.forName(packageName + "." + normalized);
            } catch (ClassNotFoundException ignored) {
            }

            if (clazz != null) {
                return clazz;
            }
        }

        throw new InputException(new ClassNotFoundException(normalized + " not present in Bukkit entity namespace"));
    }

    @Nullable
    private static Entity getEntity(String input, @Nullable CommandSender sender) throws InputException {
        Entity target;
        if (sender instanceof Player) {
            if (input.equalsIgnoreCase("that")) {
                target = PlatformUtil.getEntityPlayerLookingAt((Player) sender, 25, 1.5D);

                if (target != null) {
                    return target;
                }
            } else if (input.equalsIgnoreCase("me")) {
                return ((Player) sender);
            }
        }

        Location loc = getLocation(input, sender);
        return PlatformUtil.getEntityNearestTo(loc, 25, 1.5D);
    }
}
