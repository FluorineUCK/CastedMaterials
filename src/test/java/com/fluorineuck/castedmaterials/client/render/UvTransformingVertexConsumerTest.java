package com.fluorineuck.castedmaterials.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UvTransformingVertexConsumerTest {
    @Test
    void transformsUvAndPassesOtherAttributesThrough() {
        Recording delegate = new Recording();
        VertexConsumer wrapped = new UvTransformingVertexConsumer(delegate,
                new UvTransform(0.25f, 0.5f, 0.25f, 0.25f));
        wrapped.addVertex(1, 2, 3).setColor(4, 5, 6, 7).setUv(0.5f, 0.75f)
                .setUv1(8, 9).setUv2(10, 11).setNormal(0, 1, 0);
        assertEquals(0.375f, delegate.u);
        assertEquals(0.6875f, delegate.v);
        assertEquals(1, delegate.x);
        assertEquals(4, delegate.r);
        assertEquals(10, delegate.lightU);
    }

    private static final class Recording implements VertexConsumer {
        float x,u,v; int r,lightU;
        public VertexConsumer addVertex(float x,float y,float z){this.x=x;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){this.r=r;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){this.lightU=u;return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
