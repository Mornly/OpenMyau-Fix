package myau.events;

import myau.event.events.callables.EventCancellable;

public class WindowClickEvent extends EventCancellable {
    private final int windowsId;
    private final int slotId;
    private final int mouseButtonClicked;
    private final int mode;

    public WindowClickEvent(int windowsId, int slotId, int mouseButtonClicked, int mode) {
        this.windowsId = windowsId;
        this.slotId = slotId;
        this.mouseButtonClicked = mouseButtonClicked;
        this.mode = mode;
    }
<<<<<<< HEAD

    // 添加以下 getter
    public int getWindowsId() {
        return windowsId;
    }

    public int getSlotId() {
        return slotId;
    }

    public int getMouseButtonClicked() {
        return mouseButtonClicked;
    }

    public int getMode() {
        return mode;
    }
}
=======
}
>>>>>>> e7b1dde9d663c728a1ea63266e41a22ddd0a1e61
