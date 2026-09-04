package com.windsurf.model;

public record WindData(
    double speedMs,
    double direction,
    double gustsMs,
    String directionLabel
) {}
