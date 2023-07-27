import java.util.concurrent.Callable;
import java.io.File;

import by.gsu.dl.taskconv.Main;
import by.gsu.dl.taskconv.RageExitException;

class RunTest {
	public static final void main (String[] args) {
		for (String path : args) {
			try {
				Callable<String> taskConv = Main.init(new File(".."),
						"--auto", "--quiet", "--move",
						"--level", "1", path);
				System.out.printf("Detected type '%s' for path '%s'%n",
						taskConv.call(), path);
			} catch (RageExitException e) {
				System.out.printf("Produced error '%s' for path '%s'%n",
						e.getMessage(), path);
			} catch (Exception e) { // unexpected error (will only be an unchecked one)
				System.out.println(path + ": " + e);
				e.printStackTrace();
			}
		}
	}
}
