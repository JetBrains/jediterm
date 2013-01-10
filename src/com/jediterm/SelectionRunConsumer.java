/**
 * 
 */
package com.jediterm;

import java.awt.Point;

import org.apache.log4j.Logger;

public class SelectionRunConsumer implements StyledRunConsumer {
	private static final Logger logger = Logger.getLogger(SelectionRunConsumer.class);
	private final StringBuffer selection;
	private final Point begin;
	private final Point end;

	boolean first = true;

	public SelectionRunConsumer(final StringBuffer selection, final Point begin, final Point end) {
		this.selection = selection;
		this.end = end;
		this.begin = begin;
	}

	public void consumeRun(final int x, final int y, final TermStyle style, final char[] buf, final int start, final int len) {
		int startPos = start;
		int extent = len;
		
		if(y == end.y){
			extent = Math.min(end.x  - x, extent);
			
		}
		if(y == begin.y ){
			final int xAdj = Math.max(0, begin.x - x);
			startPos += xAdj;
			extent -= xAdj;
			if( extent < 0) return; 
		}
		if(extent < 0) return; // The run is off the left edge of the selection on the first line, 
							   //  or off the right edge on the last line.
		if(len > 0){
			if(!first && x == 0) selection.append('\n');
			first = false;
			if( startPos < 0 ){
				logger.error("Attempt to copy to selection from before start of buffer");
			}else if (startPos + extent >= buf.length){
				logger.error("Attempt to copy to selection from after end of buffer");
			}else{
				selection.append(buf,startPos, extent);
			}
		}
	}
}