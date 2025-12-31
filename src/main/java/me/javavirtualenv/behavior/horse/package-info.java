/**
 * Horse-specific behaviors for the Better Ecology mod.
 * <p>
 * This package implements scientifically-based horse behaviors including:
 * <ul>
 *   <li>{@link me.javavirtualenv.behavior.horse.KickDefenseGoal} - Rear kick defense when attacked from behind</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.RearingGoal} - Rearing behavior when frightened or angry</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.BondingGoal} - Bonding system with players for improved obedience</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.HerdDynamicsGoal} - Wild horse band behavior with lead stallion</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.SocialGroomingGoal} - Social bonding through mutual grooming</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.HorseBondData} - Bond level storage and management</li>
 *   <li>{@link me.javavirtualenv.behavior.horse.HorseBehaviorHandle} - Registration handle for all horse behaviors</li>
 * </ul>
 * <p>
 * These behaviors are based on equine ethology research:
 * <ul>
 *   <li>Horses use rear kicks as primary defense against predators from behind</li>
 *   <li>Rearing is a fear/excitement response that also makes them look larger</li>
 *   <li>Domestic horses form bonds with frequent riders, showing increased obedience</li>
 *   <li>Wild horses form bands with a lead stallion protecting mares and foals</li>
 *   <li>Social grooming (allogrooming) strengthens herd bonds</li>
 * </ul>
 * <p>
 * Configuration is done through JSON files in:
 * {@code src/main/resources/data/better-ecology/mobs/passive/horse/behaviors.json}
 */
package me.javavirtualenv.behavior.horse;
