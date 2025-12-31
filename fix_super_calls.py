import re
import os

# List of files to fix
files = [
    'src/main/java/me/javavirtualenv/behavior/aquatic/AxolotlHuntingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/AxolotlPlayDeadBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/CurrentRidingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/DolphinTreasureHuntBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/DolphinWaveRidingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/GlowSquidPreyAttractionBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/InkCloudBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/PufferfishInflateBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/SalmonUpstreamBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/TadpoleMetamorphosisBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/aquatic/VerticalMigrationBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/ClimbingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/CreeperDetectionBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/CreepingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/FallDamageReductionBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/GiftGivingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/HissBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/PhantomRepelBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/PlayBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/PounceBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/PurrBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/QuietMovementBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/RubAffectionBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/SleepOnBlocksBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/SqueezeThroughGapsBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/feline/StalkBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/frog/CroakingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/frog/FrogJumpingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/frog/FrogSwimmingBehavior.java',
    'src/main/java/me/javavirtualenv/behavior/sniffer/SnifferSocialBehavior.java',
]

fixed_count = 0
for file_path in files:
    if not os.path.exists(file_path):
        print(f'SKIP: {file_path} not found')
        continue

    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Pattern 1: super(weight, boolean)
    def replace_two_params(match):
        weight = match.group(1)
        enabled = match.group(2)
        return f'super();\n        setWeight({weight});\n        setEnabled({enabled});'

    content = re.sub(
        r'super\(([0-9.]+),\s*(true|false)\s*\);',
        replace_two_params,
        content
    )

    # Pattern 2: super(weight)
    def replace_one_param(match):
        weight = match.group(1)
        return f'super();\n        setWeight({weight});'

    content = re.sub(
        r'super\(([0-9.]+)\);',
        replace_one_param,
        content
    )

    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        fixed_count += 1
        print(f'FIXED: {file_path}')
    else:
        print(f'NO CHANGE: {file_path}')

print(f'\nTotal files fixed: {fixed_count}')
