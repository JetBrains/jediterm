package com.jediterm;

public interface StyledRunConsumer {
	void consumeRun(int x, int y, TermStyle style, char[] buf, int start, int len);
}
