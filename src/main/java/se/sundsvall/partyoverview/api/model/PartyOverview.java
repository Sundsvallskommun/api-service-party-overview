package se.sundsvall.partyoverview.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "PartyOverview model, everything the source systems have reported about a party")
public class PartyOverview {

	@Schema(description = "Assets the party holds")
	private List<Asset> assets;

	public static PartyOverview create() {
		return new PartyOverview();
	}

	public List<Asset> getAssets() {
		return assets;
	}

	public void setAssets(final List<Asset> assets) {
		this.assets = assets;
	}

	public PartyOverview withAssets(final List<Asset> assets) {
		this.assets = assets;
		return this;
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final PartyOverview that = (PartyOverview) o;
		return Objects.equals(assets, that.assets);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(assets);
	}

	@Override
	public String toString() {
		return "PartyOverview{" +
			"assets=" + assets +
			'}';
	}
}
