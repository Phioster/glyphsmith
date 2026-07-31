# Glyphsmith Architecture

## 1. Purpose

This document defines the intended architecture of Glyphsmith.

It describes the target dependency direction, module boundaries, internal
plugin model, compatibility rules, and migration strategy.

This is a target architecture. It does not mean that every class already
follows the structure described here.

Architectural migrations must be performed incrementally. Existing rendering,
presets, exports, and project behavior must be preserved unless a task
explicitly changes them.

---

## 2. Product Model

Glyphsmith is a native Android host application for image and video
transformation.

Its primary capabilities are:

- image and video input
- source adjustment
- image sampling
- dithering and quantisation
- palette mapping
- render modules
- stackable image effects
- layers
- parameter animation
- image, video, vector, and text export

Glyph Art is one render module inside this system.

Glyph Art is not the owner of:

- image sources
- dithering
- quantisation
- palettes
- image effects
- animation
- layers
- image export
- project state

The primary application workflow is Pixel Dither.

---

## 3. High-Level Pipeline

The intended processing pipeline is:

```text
Media Source
    |
    v
Source Decode and Orientation
    |
    v
Source Adjustments
    |
    v
Sampling
    |
    v
Quantisation or Dithering
    |
    v
Render Module
    |
    v
Image Effect Pipeline
    |
    v
Layer Compositor
    |
    v
Preview or Export

