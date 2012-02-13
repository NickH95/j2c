package se.arnetheduck.j2c.test;

public class ExtendsTest extends LocalClassTest {
	public int nestedFieldAccess() {
		return new ParamConstructor(3) {
			@Override
			public int getV() {
				return lct.testArrayAccess();
			}
		}.getV();
	}

	public int nestedMethodAccess() {
		return new ParamConstructor(3) {
			@Override
			public int getV() {
				return testArrayAccess();
			}
		}.getV();
	}

	public int testThis(final int x) {
		ParamConstructor lpc = new ParamConstructor(3) {
			@Override
			public int getV() {
				return ExtendsTest.this.testArrayAccess();
			}
		};

		return lpc.getV();
	}
}
