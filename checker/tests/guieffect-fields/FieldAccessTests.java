import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.guieffect.qual.UIType;

public class FieldAccessTests {

    /* TODO: Need to test instance and static field initializer contexts!
     * This includes testing instance initializers in UITypes and normal
     * types, both with and without constructors, and ensuring all
     * constructors' effects include the instance field initializers'
     * effects.
     */

    @UIType
    public static class UITypeWithFields {
        public int x;

        public static int y;
    }

    public static class OtherClass {
        @UIEffect int uifield;
        int safefield;

        public void accesses() {

            safefield = 2;

            //:: error: fieldaccess.invalid.ui
            uifield = 2;
        }
    }

    public static void safeContext(UITypeWithFields ui, OtherClass other) {

        int x = other.safefield;
        other.safefield = 9;

        //:: error: fieldaccess.invalid.ui
        int y = other.uifield;
        //:: error: fieldaccess.invalid.ui
        other.uifield = 10;
        //:: error: fieldaccess.invalid.ui
        y = ui.x;
        //:: error: fieldaccess.invalid.ui
        ui.x = 12;
        //:: error: fieldaccess.invalid.ui
        y = UITypeWithFields.y;
        //:: error: fieldaccess.invalid.ui
        UITypeWithFields.y = 14;
    }

    @UIEffect
    public static void uiContext() {

        UITypeWithFields ui = new UITypeWithFields();

        OtherClass other = new OtherClass();

        safeContext(ui, other);

        // Safe field accesses
        int x = other.safefield;
        other.safefield = 9;

        // UI field accesses
        int y = other.uifield;
        other.uifield = 10;
        y = ui.x;
        ui.x = 12;
    }
}
