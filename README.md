#简介
最近在闲逛的时候，发现了一款**粒子爆炸特效**的控件，觉得比较有意思，效果也不错。
但是代码不好扩展，也就是说如果要提供不同的爆炸效果，需要修改的地方比较多。于是我对源代码进行了一些**重构**，将爆炸流程和粒子运动分离。
对于源码，大家可以参考以下链接
[链接1](https://github.com/tyrantgit/ExplosionField)
[链接2](http://www.itlanbao.com/code/20151121/11557/100655.html)

上面两套代码，其实结构都是一样的，但是实现的效果不同(其实就是粒子运动的算法不同)。
本篇文章，将给大家介绍粒子爆炸特效的**实现方式**，替大家理清实现思路。

实现效果如下：
![这里写图片描述](http://img.blog.csdn.net/20151202143834990)
<br><br>
#类设计
类设计图如下：
![这里写图片描述](http://img.blog.csdn.net/20151202145656706)

> **ExplosionField**，**爆炸效果发生的场地**，是一个View。当一个控件需要爆炸时，需要为控件生成一个ExplosionField，这个ExplosionField**覆盖整个屏幕**，于是我们才能看到完整的爆炸效果。
> 
> **ExplosionAnimator**，爆炸动画，其实是一个**计时器**，继承自ValueAnimator。1024s内，完成爆炸动画，每次计时，就更新所有粒子的运动状态。**draw()方法**是它最重要的方法，也就是使所有粒子重绘自身，从而实现动画效果。
> 
> **ParticleFactory**，是一个抽象类。用于**产生粒子数组**，不同的ParticleFactory可以产生不同类型的粒子数组。
> 
> **Particle**，抽象的粒子类。代表粒子本身，必须拥有的属性包括，当前自己的**cx,cy坐标和颜色color**。必须实现两个方法，d**raw()方法选择怎么绘制自身**(圆形还是方形等),**caculate()计算当前时间，自己所处的位置**。
 <br><br>
#控件的使用
控件使用很简单，首先要实现不同的爆炸效果，需要**给ExplosionField传入不同的ParticleFactory工厂**，产生不同的粒子。
```
ExplosionField explosionField = new ExplosionField(this,new FallingParticleFactory());
```
然后哪个控件需要爆炸效果，就这样添加
```
explosionField.addListener(findViewById(R.id.text));
explosionField.addListener(findViewById(R.id.layout1));
```
这样就为两个控件添加了爆炸效果，注意**layout1代表的是一个viewgroup，那么我们就会为viewgroup中的每个view添加爆炸效果**。
我们可以想象，**在ExplosionField的构造函数中，传入不同的ParticleFactory，就可以生成不同的爆炸效果。**
<br><br>
#爆炸实现思路
##1、获取当前控件背景bitmap
例如，例子中使用的是imageview，对于这个控件，我提供了一个**工具类**，可以获得其背景的Bitmap对象

```
public static Bitmap createBitmapFromView(View view) {
        view.clearFocus();
        Bitmap bitmap = createBitmapSafely(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888, 1);
        if (bitmap != null) {
            synchronized (sCanvas) {
                Canvas canvas = sCanvas;
                canvas.setBitmap(bitmap);
                view.draw(canvas);
                canvas.setBitmap(null);
            }
        }
        return bitmap;
    }

    public static Bitmap createBitmapSafely(int width, int height, Bitmap.Config config, int retryCount) {
        try {
            return Bitmap.createBitmap(width, height, config);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            if (retryCount > 0) {
                System.gc();
                return createBitmapSafely(width, height, config, retryCount - 1);
            }
            return null;
        }
    }
```

上面的方法，简而言之，就是**将控件的Bitmap对象复制了一份，然后返回。**
我们知道，**bitmap可以看成是一个像素矩阵，矩阵上面的点，就是一个个带有颜色的像素，于是我们可以获取每个点(未必需要每个)的颜色和位置，组装成一个对象Particle，这么一来，Particle就代表带有颜色的点了。**
##2、将背景bitmap转换成Particle数组
获取Bitmap以后，我们交给ParticleFactory进行加工，根据Bitmap生产Particle数组。
```
public abstract class ParticleFactory {
    public abstract Particle[][] generateParticles(Bitmap bitmap, Rect bound);
}
```
例如我们来看一个简单实现类，也是gif图中，第一个下落效果的工厂类

```
public class FallingParticleFactory extends ParticleFactory{
    public static final int PART_WH = 8; //默认小球宽高

    public Particle[][] generateParticles(Bitmap bitmap, Rect bound) {
        int w = bound.width();//场景宽度
        int h = bound.height();//场景高度

        int partW_Count = w / PART_WH; //横向个数
        int partH_Count = h / PART_WH; //竖向个数

        int bitmap_part_w = bitmap.getWidth() / partW_Count;
        int bitmap_part_h = bitmap.getHeight() / partH_Count;

        Particle[][] particles = new Particle[partH_Count][partW_Count];
        Point point = null;
        for (int row = 0; row < partH_Count; row ++) { //行
            for (int column = 0; column < partW_Count; column ++) { //列
                //取得当前粒子所在位置的颜色
                int color = bitmap.getPixel(column * bitmap_part_w, row * bitmap_part_h);

                float x = bound.left + FallingParticleFactory.PART_WH * column;
                float y = bound.top + FallingParticleFactory.PART_WH * row;
                particles[row][column] = new FallingParticle(color,x,y,bound);
            }
        }

        return particles;
    }

}
```
其中**Rect类型的bound，是代表原来View控件的宽高信息**。
根据我们设定的每个**粒子的大小**，和**控件的宽高**，我们就可以计算出，有多少个粒子组成这个控件的背景。
我们取得每个粒子所在位置的颜色，位置，用于生产粒子，这就是FallingParticle。
##3、生成爆炸场地，开始爆炸动画流程
**爆炸时需要场地的**，也就是绘制粒子的地方，我们通过给当前屏幕，添加一个覆盖全屏幕的ExplosionField来作为爆炸场地。

```
public class ExplosionField extends View{
		...
		
		/**
	     * 给Activity加上全屏覆盖的ExplosionField
	     */
	    private void attach2Activity(Activity activity) {
	        ViewGroup rootView = (ViewGroup) activity.findViewById(Window.ID_ANDROID_CONTENT);
	
	        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
	                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
	        rootView.addView(this, lp);
	    }
		...
}
```
爆炸场地添加以后，我们响应控件的点击事件，开始动画
首先是**震动动画**

```
 /**
     * 爆破
     * @param view 使得该view爆破
     */
    public void explode(final View view) {
        //防止重复点击
        if(explosionAnimatorsMap.get(view)!=null&&explosionAnimatorsMap.get(view).isStarted()){
            return;
        }
        if(view.getVisibility()!=View.VISIBLE||view.getAlpha()==0){
            return;
        }
        //为了正确绘制粒子
        final Rect rect = new Rect();
        view.getGlobalVisibleRect(rect); //得到view相对于整个屏幕的坐标
        int contentTop = ((ViewGroup)getParent()).getTop();
        Rect frame = new Rect();
        ((Activity) getContext()).getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
        int statusBarHeight = frame.top;
        rect.offset(0, -contentTop - statusBarHeight);//去掉状态栏高度和标题栏高度

        //震动动画
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f).setDuration(150);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            Random random = new Random();

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                view.setTranslationX((random.nextFloat() - 0.5f) * view.getWidth() * 0.05f);
                view.setTranslationY((random.nextFloat() - 0.5f) * view.getHeight() * 0.05f);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                explode(view, rect);//爆炸动画
            }
        });
        animator.start();
    }
```
**震动动画很简单，就是x,y方向上，随机产生一些位移，使原控件发生移动即可。**
在震动动画的最后，调用了爆炸动画，于是爆炸动画开始。

```
private void explode(final View view,Rect rect) {
        final ExplosionAnimator animator = new ExplosionAnimator(this, Utils.createBitmapFromView(view), rect,mParticleFactory);
        explosionAnimators.add(animator);
        explosionAnimatorsMap.put(view, animator);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                //缩小,透明动画
                view.animate().setDuration(150).scaleX(0f).scaleY(0f).alpha(0f).start();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();

                //动画结束时从动画集中移除
                explosionAnimators.remove(animation);
                explosionAnimatorsMap.remove(view);
                animation = null;
            }
        });
        animator.start();
    }
```
爆炸动画首先**将原控件隐藏**。
我们来看爆炸动画的具体实现

```
public class ExplosionAnimator extends ValueAnimator {
    ...

    public ExplosionAnimator(View view, Bitmap bitmap, Rect bound,ParticleFactory particleFactory) {
        mParticleFactory = particleFactory;
        mPaint = new Paint();
        mContainer = view;
        setFloatValues(0.0f, 1.0f);
        setDuration(DEFAULT_DURATION);
        mParticles = mParticleFactory.generateParticles(bitmap, bound);
    }
    //最重要的方法
    public void draw(Canvas canvas) {
        if(!isStarted()) { //动画结束时停止
            return;
        }
        //所有粒子运动
        for (Particle[] particle : mParticles) {
            for (Particle p : particle) {
                p.advance(canvas,mPaint,(Float) getAnimatedValue());
            }
        }
        mContainer.invalidate();
    }

    @Override
    public void start() {
        super.start();
        mContainer.invalidate();
    }
}
```
实现很简单，就是根据工厂类，生成粒子数组。
而其实质是一个ValueAnimator，在一定时间内，从0数到1。
然后提供了一个draw()方法，方法里面调**用了每个粒子的advance()方法**，并且传入了当前数到的数字(是一个小数)。
advance()方法里，其实调用了draw()方法和caculate()方法。
<br>
上面的实现，**其实是一个固定的流程，添加了爆炸场地以后，我们就开始从0数到1，在这个过程中，粒子会根据当前时间，绘制自己的位置，所以粒子的位置，其实是它自己决定的，和流程无关。**
也就是说，我们只要用不同的算法，绘制粒子的位置即可，实现了流程和粒子运动的分离。
##4、怎么运动？粒子自己最清楚
举个例子，gif图中，下落效果的粒子是这样运动的

```
public class FallingParticle extends Particle{
    static Random random = new Random();
    float radius = FallingParticleFactory.PART_WH;
    float alpha = 1.0f;
    Rect mBound;
    /**
     * @param color 颜色
     * @param x
     * @param y
     */
    public FallingParticle(int color, float x, float y,Rect bound) {
        super(color, x, y);
        mBound = bound;
    }
    ...
    protected void caculate(float factor){
        cx = cx + factor * random.nextInt(mBound.width()) * (random.nextFloat() - 0.5f);
        cy = cy + factor * random.nextInt(mBound.height() / 2);

        radius = radius - factor * random.nextInt(2);

        alpha = (1f - factor) * (1 + random.nextFloat());
    }
}

```
caculate(float factor)方法，根据当前时间，计算粒子的下一个位置
我们可以看到，在这个粒子中，cy也就是竖直方向上是不断增加的，cx也就是水平方向上，是随机增加或者减少，这样就形成了下落效果。
计算出当前位置以后，粒子就将自己绘制出来
```
protected void draw(Canvas canvas,Paint paint){
        paint.setColor(color);
        paint.setAlpha((int) (Color.alpha(color) * alpha)); //这样透明颜色就不是黑色了
        canvas.drawCircle(cx, cy, radius, paint);
    }
```
<br><br>
#怎么扩展？
从上面的代码结构可以看出，**爆炸流程和粒子具体运动无关**，最重要的是，我们要**实现自己的caculate()方法，决定粒子的运动形态**。
而不同的粒子可以由对应的工厂产生，所以要扩展爆炸特性，**只需要定义一个粒子类，和生成粒子类的工厂即可。**

[源码下载](http://download.csdn.net/detail/kangaroo835127729/9325565)
[github地址](https://github.com/835127729/ViewExplosion)

