package com.example.cruscotto.model;

import java.util.Map;

public record ScheduledJobInfo(String procedureName,
							   String scheduleType,
							   String scheduleExpression,
							   Map<String, Object> parameters) {
}
