import com.friendswine.client.LeashAnimation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 独立断言检查生产补偿函数；不依赖启动游戏或人工观察绳子方向。 */
public final class LeashAnimationCheck {
    private static void near(Vector3f actual, Vector3f expected) {
        if (actual.distance(expected) > 0.0001f) {
            throw new AssertionError(actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        int cases = 0;
        for (float angle : new float[]{0, 0.7f, 1.5707964f, 3.1415927f, 5.2f}) {
            for (float width : new float[]{1, 1.5f, 2, 3}) {
                for (float height : new float[]{1, 0.5f, 0.1f}) {
                    Matrix4f animation = new Matrix4f().rotateY(angle).scale(width, height, 1);
                    Vector3f holder = new Vector3f(7.3f, 2.1f, -4.2f);
                    Vector3f local = LeashAnimation.inverseHolder(animation, holder.x, holder.y, holder.z);
                    near(animation.transformPosition(new Vector3f(local)), holder);

                    // 原版在生物挂点平移后插值绳段：起点随模型，终点必须回到真实持绳位置。
                    Vector3f offset = new Vector3f(0.3f, 1.2f, -0.4f);
                    Matrix4f leash = new Matrix4f(animation).translate(offset);
                    near(leash.transformPosition(new Vector3f()), animation.transformPosition(new Vector3f(offset)));
                    near(leash.transformPosition(new Vector3f(local).sub(offset)), holder);
                    near(LeashAnimation.inverseHolder(animation, holder.x, holder.y, holder.z), local);
                    cases++;
                }
            }
        }
        near(LeashAnimation.inverseHolder(new Matrix4f(), 7, 2, -4), new Vector3f(7, 2, -4));
        System.out.println("PASS " + cases + " rotation/scale cases; holder fixed, mob attachment animated, repeat stable");
    }
}
