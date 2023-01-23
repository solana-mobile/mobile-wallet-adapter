package android.os;

/**
 * Mocks Android's Looper class (android.os.Looper) so that we can test code that
 * uses the Looper abstraction methods without needed to manually mock or inject a Looper
 * abstraction for every test.
 *
 * This mock is very basic, it does the minimum amount of work to provide a functional test env.
 */
public class Looper {

    public static void prepare() {}
    public static void loop() {}
    public static Looper myLooper() {
        return new Looper();
    }
}
