package com.quadrant;

import lombok.Data;

/**
 * Failure counts for a single physical object at grid position (x, y).
 *
 * sheet1 = top-left quadrant failures
 * sheet2 = top-right quadrant failures
 * sheet3 = bottom-left quadrant failures
 * sheet4 = bottom-right quadrant failures
 */
@Data
public class FailXY {
    private int  x;
    private int  y;
    private long sheet1;
    private long sheet2;
    private long sheet3;
    private long sheet4;
}
