package com.fluorineuck.castedmaterials.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class UvTransformingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final UvTransform transform;
    public UvTransformingVertexConsumer(VertexConsumer delegate, UvTransform transform) {
        this.delegate = delegate; this.transform = transform;
    }
    public VertexConsumer addVertex(float x,float y,float z){delegate.addVertex(x,y,z);return this;}
    public VertexConsumer setColor(int r,int g,int b,int a){delegate.setColor(r,g,b,a);return this;}
    public VertexConsumer setUv(float u,float v){delegate.setUv(transform.mapU(u),transform.mapV(v));return this;}
    public VertexConsumer setUv1(int u,int v){delegate.setUv1(u,v);return this;}
    public VertexConsumer setUv2(int u,int v){delegate.setUv2(u,v);return this;}
    public VertexConsumer setNormal(float x,float y,float z){delegate.setNormal(x,y,z);return this;}
}
