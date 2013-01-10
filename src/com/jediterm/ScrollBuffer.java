package com.jediterm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.log4j.Logger;

public class ScrollBuffer implements StyledRunConsumer {
	private static final Logger logger = Logger.getLogger(ScrollBuffer.class);

	private static final int BUF_SIZE = 8192;
	private static final int RUN_SIZE = 128;
	static class Section{
		int width;
		char[] buf = new char[BUF_SIZE];
		int[] runStarts = new int[RUN_SIZE];
		TermStyle[] runStyles = new TermStyle[RUN_SIZE];
		BitSet lineStarts = new BitSet(RUN_SIZE);

		public void setLineStart(final int currentRun) {

		}

		public int putRun(final int currentRun,
						  final int bufPos,
						  final boolean isNewLine,
						  final TermStyle style,
						  final char[] otherBuf,
						  final int start,
						  final int len) {
			if ( bufPos + len >= buf.length ){
				complete(currentRun, bufPos);
				return -1;
			}
			ensureArrays(currentRun);
			lineStarts.set(currentRun, isNewLine);
			runStarts[currentRun] = bufPos;
			runStyles[currentRun] = style;
			System.arraycopy(otherBuf, start, buf, bufPos, len);

			return bufPos + len;
		}

		private void ensureArrays(final int currentRun) {
			if(currentRun >= runStarts.length){
				runStarts = Util.copyOf(runStarts, runStarts.length * 2);
				runStyles = Util.copyOf(runStyles, runStyles.length * 2);
			}
		}

		public void complete(final int currentRun, final int bufPos) {
			runStarts = Util.copyOf(runStarts, currentRun);
			runStyles = Util.copyOf(runStyles, currentRun);
			buf = Util.copyOf(buf, bufPos);
			lineStarts = lineStarts.get(0, currentRun);
		}

		// for a complete section
		public int pumpRunsComplete(final int firstLine, final int startLine, final int endLine,  final StyledRunConsumer consumer){
			return pumpRunsImpl(firstLine, startLine, endLine, consumer, buf.length);
		}

		// for a current section
		public int pumpRunsCurrent(final int firstLine, final int startLine, final int endLine,  final StyledRunConsumer consumer, final int bufPos){
			return pumpRunsImpl(firstLine, startLine, endLine, consumer, bufPos);
		}

		private int pumpRunsImpl(final int firstLine, final int startLine, final int endLine,  final StyledRunConsumer consumer, final int bufferEnd) {
			int x = 0;
			int y = firstLine - 1;
			for(int i = 0; i < runStarts.length; i++ ){
				if(lineStarts.get(i)){
					x = 0;
					y++;
				}
				if(y < startLine ) continue;
				if(y > endLine ) break;
 				final int runStart = runStarts[i];
				int runEnd;
				boolean last = false;
				// if we are at the end of the array, or the next runstart is 0 ( ie unfilled),
				// this is the last run.
				if( i == runStarts.length -1 || runStarts[i + 1] == 0){
					runEnd = bufferEnd ;
					last = true;
				} else
					runEnd = runStarts[i + 1] ;

				consumer.consumeRun(x, y, runStyles[i] , buf, runStart, runEnd - runStart );
				x+= runEnd - runStart;
				if(last) break;
			}
			return y;
		}

		int getLineCount(){
			return lineStarts.cardinality();
		}
	}

	List<Section> completeSections = new ArrayList<Section>();

	Section currentSection;
	int currentRun;
	int bufPos;
	private int totalLines;

	public ScrollBuffer(){
		newSection();
	}

	private void newSection() {
		currentSection = new Section();
		currentRun = -1;
		bufPos = 0;
	}

	public synchronized String getLines() {
		final StringBuffer sb = new StringBuffer();

		final StyledRunConsumer consumer = new StyledRunConsumer(){
			public void consumeRun(int x, int y, TermStyle style, char[] buf, int start, int len) {
				if(x == 0) sb.append('\n');
				sb.append(buf,start, len);
			}
		};
		int currentLine = -totalLines;
		for(final Section s: completeSections)
			currentLine = s.pumpRunsComplete(currentLine, currentLine, 0, consumer);

		currentSection.pumpRunsCurrent(currentLine, currentLine, 0, consumer, bufPos);

		return sb.toString();
	}

	public synchronized void  consumeRun(final int x, final int y, final TermStyle style, final char[] buf, final int start, final int len) {
		currentRun++;
		final boolean isNewLine = x == 0;
		if(isNewLine) totalLines++;
		bufPos = currentSection.putRun(currentRun, bufPos, isNewLine, style, buf, start, len  );
		if (bufPos < 0){

			completeSections.add(currentSection);
			newSection();
			currentRun++;
			bufPos = currentSection.putRun(currentRun, bufPos, isNewLine, style, buf, start, len);
			if(bufPos < 0)
				logger.error("Can not put run in new section, bailing out");
		}
	}

	public int getLineCount() {
		return totalLines;
	}

	public void pumpRuns(final int firstLine, final int height, final StyledRunConsumer consumer) {
		// firstLine is negative . 0 is the first line in the back buffer.
		// Find start Section
		int currentLine = -currentSection.getLineCount();
		final int lastLine = firstLine + height;
		if( currentLine > firstLine  ){
			//Need to look at past sections
			//Look back through them to find the one that contains our first line.
			int i = completeSections.size() -1;
			for(;i >= 0; i--){
				currentLine -= completeSections.get(i).getLineCount();
				if(currentLine <= firstLine)
					// This section contains our first line.
					break;
			}
			i = Math.max( i , 0); // if they requested before this scroll buffer return as much as possible.
			for(; i < completeSections.size(); i++){
				final int startLine = Math.max(firstLine, currentLine);
				final Section s = completeSections.get(i);
				currentLine = s.pumpRunsComplete(currentLine, startLine, lastLine, consumer);
				if(currentLine >= lastLine) break;
			}
		}
		if(currentLine < lastLine)
			currentSection.pumpRunsCurrent(currentLine, Math.max(firstLine, currentLine) , lastLine, consumer, bufPos);
	}

}
