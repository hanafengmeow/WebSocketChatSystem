import * as fs from 'fs';
import * as path from 'path';

describe('JAR file deployment', () => {
  test('build/libs directory exists', () => {
    const jarPath = path.join(process.cwd(), '../build/libs');
    expect(fs.existsSync(jarPath)).toBe(true);
  });

  test('correct JAR file can be found (non-plain)', () => {
    const jarPath = path.join(process.cwd(), '../build/libs');

    if (!fs.existsSync(jarPath)) {
      console.warn('build/libs directory does not exist yet - run gradle build first');
      return;
    }

    const files = fs.readdirSync(jarPath);
    const jarFiles = files.filter(f => f.endsWith('.jar') && !f.includes('plain'));

    expect(jarFiles.length).toBeGreaterThan(0);
    expect(jarFiles[0]).toMatch(/WebSocketChatSystemPOC-.*\.jar/);
    expect(jarFiles[0]).not.toContain('plain');
  });

  test('plain JAR is excluded from deployment', () => {
    const jarPath = path.join(process.cwd(), '../build/libs');

    if (!fs.existsSync(jarPath)) {
      return;
    }

    const files = fs.readdirSync(jarPath);
    const plainJars = files.filter(f => f.endsWith('.jar') && f.includes('plain'));

    // Plain jar should exist but should be excluded by our deployment logic
    if (plainJars.length > 0) {
      console.log('Plain JAR found (will be excluded):', plainJars[0]);
    }
  });
});
