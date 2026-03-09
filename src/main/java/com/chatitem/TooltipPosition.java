package com.chatitem;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TooltipPosition
{
	BELOW("Below mouse"),
	ABOVE("Above mouse");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
