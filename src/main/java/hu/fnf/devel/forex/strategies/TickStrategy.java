package hu.fnf.devel.forex.strategies;

import hu.fnf.devel.forex.states.State;

import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;

public abstract class TickStrategy extends Strategy{
	public abstract int signalStrength(Instrument instrument, ITick tick);
	abstract public State onStart(Instrument instrument, ITick tick);
}