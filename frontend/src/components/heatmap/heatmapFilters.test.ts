import { describe, expect, it } from 'vitest';
import { lastDaysRange } from './heatmapFilters';

describe('lastDaysRange', () => {
    it('7 días spans 7 calendar days including today', () => {
        const { from, to } = lastDaysRange(7, new Date(2026, 8, 18));
        expect(new Date(from).getDate()).toBe(12);
        expect(new Date(to).getDate()).toBe(18);
    });
});
