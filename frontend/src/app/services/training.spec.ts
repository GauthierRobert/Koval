import { TestBed } from '@angular/core/testing';

import { Training } from './training';

describe('Training', () => {
  let service: Training;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Training);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
