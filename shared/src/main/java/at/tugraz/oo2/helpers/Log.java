package at.tugraz.oo2.helpers;

public final class Log {

    // used to turn off and on log messages
    // false -> no log messages
    // true  -> shows log messages
    // IMPORTANT! SET TO 'false' BEFORE FINAL COMMIT!
    private final static boolean logSwitch = false;

    //ref colors from:
    // https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println?fbclid=IwAR0_N-4fAjz4LoQrjtnRaj7MkzgXnrJkRoYXj2zT2EZrlMnk1YDbnWBWAFE
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";


    public static void ERROR(String error, boolean isServer) {
        if (logSwitch) {
            System.out.print(ANSI_RED + ((isServer) ? ("[SERVER][ERROR] ") : ("[CLIENT][ERROR] ")) + ANSI_RESET);
            System.out.println(error);
        }
    }

    public static void WARN(String warning, boolean isServer) {
        if (logSwitch) {
            System.out.print(ANSI_YELLOW + ((isServer) ? ("[SERVER][WARN] ") : ("[CLIENT][WARN] ")) + ANSI_RESET);
            System.out.println(warning);
        }
    }

    public static void INFO(String info, boolean isServer) {
        if (logSwitch) {
            System.out.print(ANSI_GREEN + ((isServer) ? ("[SERVER][INFO] ") : ("[CLIENT][INFO] ")) + ANSI_RESET);
            System.out.println(info);
        }
    }
}
