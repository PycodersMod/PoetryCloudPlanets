# PoetryCloudPlanets

## Supported Targets

<table>
<thead>
<tr><th>Loader</th><th>Minecraft</th></tr>
</thead>
<tbody>
<tr><td><a href="https://files.minecraftforge.net/">Forge</a></td><td><a href="https://www.minecraft.net/en-us/article/minecraft--java-edition-1-20-1">1.20.1</a></td></tr>
</tbody>
</table>

维护诗云星球模组的星球内容、资源和游戏逻辑。

## Project layout

The buildable project is in [$(@{Loader=forge; Version=1.20.1; Path=forge/1.20.1}.Path)/](forge/1.20.1/). Repository metadata remains at the root.

## Build

Run the Gradle wrapper from $(@{Loader=forge; Version=1.20.1; Path=forge/1.20.1}.Path)/:

``text
cd forge/1.20.1
./gradlew clean build
``

The target uses Forge for Minecraft 1.20.1. See the project directory for its Java and dependency requirements.
