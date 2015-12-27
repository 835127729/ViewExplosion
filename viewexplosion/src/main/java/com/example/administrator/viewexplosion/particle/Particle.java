package com.example.administrator.viewexplosion.particle;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

import java.util.Random;

/**
 * Created by azz on 15/11/19.
 * 爆破粒子
 */
public abstract class Particle {
    float cx;
    float cy;
    int color;


    /**
     * @param color 颜色
     * @param x
     * @param y
     */
    public Particle(int color, float x, float y) {
        this.color = color;
        cx = x;
        cy = y;
    }

    protected abstract void draw(Canvas canvas,Paint paint);

    protected abstract void caculate(float factor);

    public void advance(Canvas canvas,Paint paint,float factor) {
        caculate(factor);
        draw(canvas,paint);
    }
}
