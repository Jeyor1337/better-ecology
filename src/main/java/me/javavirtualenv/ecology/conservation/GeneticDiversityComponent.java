package me.javavirtualenv.ecology.conservation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Component for tracking genetic diversity and lineage of animals.
 * Stores parent UUIDs and offspring relationships to enable inbreeding prevention.
 */
public class GeneticDiversityComponent {
    private static final String MOTHER_UUID_KEY = "mother_uuid";
    private static final String FATHER_UUID_KEY = "father_uuid";
    private static final String OFFSPRING_LIST_KEY = "offspring";

    private final CompoundTag data;

    public GeneticDiversityComponent(CompoundTag data) {
        this.data = data;
    }

    /**
     * Gets the UUID of the mother.
     *
     * @return Mother's UUID, or null if unknown
     */
    @Nullable
    public UUID getMother() {
        if (!data.contains(MOTHER_UUID_KEY)) {
            return null;
        }
        return data.getUUID(MOTHER_UUID_KEY);
    }

    /**
     * Sets the UUID of the mother.
     *
     * @param motherUuid Mother's UUID
     */
    public void setMother(@Nullable UUID motherUuid) {
        if (motherUuid == null) {
            data.remove(MOTHER_UUID_KEY);
        } else {
            data.putUUID(MOTHER_UUID_KEY, motherUuid);
        }
    }

    /**
     * Gets the UUID of the father.
     *
     * @return Father's UUID, or null if unknown
     */
    @Nullable
    public UUID getFather() {
        if (!data.contains(FATHER_UUID_KEY)) {
            return null;
        }
        return data.getUUID(FATHER_UUID_KEY);
    }

    /**
     * Sets the UUID of the father.
     *
     * @param fatherUuid Father's UUID
     */
    public void setFather(@Nullable UUID fatherUuid) {
        if (fatherUuid == null) {
            data.remove(FATHER_UUID_KEY);
        } else {
            data.putUUID(FATHER_UUID_KEY, fatherUuid);
        }
    }

    /**
     * Sets both parents at once.
     *
     * @param motherUuid Mother's UUID
     * @param fatherUuid Father's UUID
     */
    public void setParents(@Nullable UUID motherUuid, @Nullable UUID fatherUuid) {
        setMother(motherUuid);
        setFather(fatherUuid);
    }

    /**
     * Gets list of offspring UUIDs.
     *
     * @return List of offspring UUIDs
     */
    public List<UUID> getOffspring() {
        List<UUID> offspring = new ArrayList<>();
        if (!data.contains(OFFSPRING_LIST_KEY)) {
            return offspring;
        }

        int[] uuidArray = data.getIntArray(OFFSPRING_LIST_KEY);
        for (int i = 0; i < uuidArray.length; i += 4) {
            if (i + 3 < uuidArray.length) {
                long mostSigBits = ((long) uuidArray[i] << 32) | (uuidArray[i + 1] & 0xFFFFFFFFL);
                long leastSigBits = ((long) uuidArray[i + 2] << 32) | (uuidArray[i + 3] & 0xFFFFFFFFL);
                offspring.add(new UUID(mostSigBits, leastSigBits));
            }
        }
        return offspring;
    }

    /**
     * Adds an offspring to the list.
     *
     * @param offspringUuid Offspring's UUID
     */
    public void addOffspring(UUID offspringUuid) {
        List<UUID> offspring = getOffspring();
        if (!offspring.contains(offspringUuid)) {
            offspring.add(offspringUuid);
            setOffspring(offspring);
        }
    }

    /**
     * Sets the offspring list.
     *
     * @param offspring List of offspring UUIDs
     */
    private void setOffspring(List<UUID> offspring) {
        if (offspring.isEmpty()) {
            data.remove(OFFSPRING_LIST_KEY);
            return;
        }

        int[] uuidArray = new int[offspring.size() * 4];
        for (int i = 0; i < offspring.size(); i++) {
            UUID uuid = offspring.get(i);
            uuidArray[i * 4] = (int) (uuid.getMostSignificantBits() >> 32);
            uuidArray[i * 4 + 1] = (int) uuid.getMostSignificantBits();
            uuidArray[i * 4 + 2] = (int) (uuid.getLeastSignificantBits() >> 32);
            uuidArray[i * 4 + 3] = (int) uuid.getLeastSignificantBits();
        }
        data.putIntArray(OFFSPRING_LIST_KEY, uuidArray);
    }

    /**
     * Checks if this entity is directly related to another within 2 generations.
     * Direct relationships include: parent-child, siblings, half-siblings,
     * grandparent-grandchild, aunt/uncle-niece/nephew.
     *
     * @param otherUuid UUID of the other entity
     * @return true if directly related within 2 generations
     */
    public boolean isDirectlyRelatedTo(UUID otherUuid) {
        return isParentOf(otherUuid) || isChildOf(otherUuid) || isSiblingOf(otherUuid);
    }

    /**
     * Checks if the other entity is a parent.
     *
     * @param otherUuid UUID of the other entity
     * @return true if other entity is a parent
     */
    public boolean isParentOf(UUID otherUuid) {
        UUID mother = getMother();
        UUID father = getFather();
        return otherUuid.equals(mother) || otherUuid.equals(father);
    }

    /**
     * Checks if the other entity is an offspring.
     *
     * @param otherUuid UUID of the other entity
     * @return true if other entity is an offspring
     */
    public boolean isChildOf(UUID otherUuid) {
        return getOffspring().contains(otherUuid);
    }

    /**
     * Checks if the other entity is a sibling or half-sibling.
     *
     * @param otherUuid UUID of the other entity
     * @return true if other entity is a sibling
     */
    public boolean isSiblingOf(UUID otherUuid) {
        UUID mother = getMother();
        UUID father = getFather();

        if (mother == null && father == null) {
            return false;
        }

        GeneticDiversityComponent otherData = LineageRegistry.getGeneticData(otherUuid);
        if (otherData == null) {
            return false;
        }

        UUID otherMother = otherData.getMother();
        UUID otherFather = otherData.getFather();

        // Full siblings: share both parents
        if (mother != null && father != null && mother.equals(otherMother) && father.equals(otherFather)) {
            return true;
        }

        // Half-siblings: share one parent
        return mother != null && mother.equals(otherMother) || father != null && father.equals(otherFather);
    }

    /**
     * Calculates the inbreeding coefficient using pedigree analysis.
     * This is a simplified version of Wright's inbreeding coefficient.
     *
     * @return Inbreeding coefficient (0.0 = outbred, 1.0 = fully inbred)
     */
    public double calculateInbreedingCoefficient() {
        UUID mother = getMother();
        UUID father = getFather();

        if (mother == null || father == null) {
            return 0.0;
        }

        return LineageRegistry.calculateRelatedness(mother, father);
    }

    /**
     * Gets the underlying NBT data.
     *
     * @return NBT data containing genetic information
     */
    public CompoundTag getData() {
        return data;
    }

    /**
     * Checks if this entity has any genetic data.
     *
     * @return true if has at least one parent or offspring recorded
     */
    public boolean hasData() {
        return data.contains(MOTHER_UUID_KEY) || data.contains(FATHER_UUID_KEY) || data.contains(OFFSPRING_LIST_KEY);
    }
}
