# Isometric Renders — Minecraft 26.2 port

> **Unofficial port** of [glisco03/isometric-renders](https://github.com/glisco03/isometric-renders) (1.21.4, MIT) to Minecraft **26.2** (Fabric, owo-lib 0.13.1+26.2).
> Not affiliated with the original author; report port bugs here, not upstream. See [DEVELOPMENT.md](DEVELOPMENT.md) for the port's state and dev workflow.
>
> **Download:** grab the jar from [Releases](../../releases) and drop it in your `mods` folder (needs Fabric API and owo-lib for 26.2).
> To update, replace the old jar with the new one. Older versions stay available on the Releases page.
>
> Port-only additions: export anti-aliasing (supersampling Off/2x/4x), correct colors for translucent blocks in exports,
> view-based translucent sorting in area renders, and a client gametest (`./gradlew runClientGameTest`).


[![curseforge](https://img.shields.io/badge/-CurseForge-gray?style=for-the-badge&logo=curseforge&labelColor=orange)](https://www.curseforge.com/minecraft/mc-mods/isometric-renders)
[![modrinth](https://img.shields.io/badge/-modrinth-gray?style=for-the-badge&labelColor=green&labelWidth=15&logo=appveyor&logoColor=white)](https://modrinth.com/mod/isometric-renders)
[![release](https://img.shields.io/github/v/release/glisco03/isometric-renders?logo=github&style=for-the-badge)](https://github.com/glisco03/isometric-renders/releases)
[![discord](https://img.shields.io/discord/825828008644313089?label=wisp%20forest&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/xrwHKktV2d)

## Overview

Isometric Renders allows you to take isometric screenshots, or rather, create renders of game objects like parts of world, blocks, items and entities.
These are automatically keyed to have a transparent background and you can adjust scale, positioning and many more options right in-game in a fancy gui.
This was designed with modded wikis or similar documentation in mind, should be easy to use and mostly pretty performant.

**In-Depth documentation is available on the [wiki](https://docs.wispforest.io/isometric-renders/home)**

#


### Currently Supported Rendering Targets

- Items
- Blocks
- Entities
- Areas of the world
- Batch export of multiple blocks or items
- Item Atlases (Overviews)
