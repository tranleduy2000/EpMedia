package VideoHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by Yj on 2017/11/13.
 * 命令集合
 */

public class CmdList extends ArrayList<String> {

	public CmdList(int initialCapacity) {
		super(initialCapacity);
	}

	public CmdList() {
	}

	public CmdList(Collection<? extends String> c) {
		super(c);
	}

	public CmdList(String[] cmds) {
		super(Arrays.asList(cmds));
	}

    public CmdList append(String s) {
		this.add(s);
		return this;
	}

	public CmdList append(int i) {
		this.add(i + "");
		return this;
	}

	public CmdList append(float f) {
		this.add(f + "");
		return this;
	}

	public CmdList append(StringBuilder sb) {
		this.add(sb.toString());
		return this;
	}

	public CmdList append(String[] ss) {
		for (String s:ss) {
			if(!s.replace(" ","").equals("")) {
				this.add(s);
			}
		}
		return this;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (String s : this) {
			sb.append(" ").append(s);
		}
		return sb.toString();
	}
}
